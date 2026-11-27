package api_tech.api_investimentos;

import api_tech.api_investimentos.identity.application.AuthenticationService;
import api_tech.api_investimentos.identity.application.RefreshTokenCodec;
import api_tech.api_investimentos.identity.domain.RefreshToken;
import api_tech.api_investimentos.identity.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Diagnostic benchmark for the owner-scoped refresh mutex.
 * It is intentionally not named *Test and is run explicitly by the CI workflow.
 * No latency threshold is asserted; only successful rotation and persisted-family integrity are gates.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "security.refresh-token.cleanup.enabled=false",
        "security.refresh-token.cleanup.retention=P7D",
        "security.refresh-token.cleanup.batch-size=10"
})
class RefreshLockContentionBenchmark {

    private static final String MYSQL_IMAGE =
            "mysql:8.4@sha256:6ea90827b1100f8f2ae306a539f86d2c264a26ed435a2a9f75551dd5c3aeb242";
    private static final int CONCURRENCY = 8;
    private static final int WARMUP_SAMPLES = 128;
    private static final int MEASURED_ROUNDS = 3;
    private static final int SAMPLES_PER_ROUND = 1_024;
    private static final int INSERT_BATCH_SIZE = 200;
    private static final int CLEANUP_BATCH_SIZE = 500;
    private static final String BENCHMARK_PASSWORD = "benchmark-password-hash";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("apiinvestimentos")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private RefreshTokenCodec refreshTokenCodec;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Test
    void measureOwnerScopedRefreshLockContentionOnMySql() throws Exception {
        System.out.printf(Locale.ROOT,
                "refresh-lock-benchmark java=%s mysql=8.4 processors=%d maxHeapMiB=%d "
                        + "concurrency=%d warmup=%d measuredRounds=%d samplesPerRound=%d%n",
                Runtime.version(),
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / (1024 * 1024),
                CONCURRENCY,
                WARMUP_SAMPLES,
                MEASURED_ROUNDS,
                SAMPLES_PER_ROUND);
        System.out.println("# Application latency includes transaction completion and access-token issuance.");
        System.out.println("# InnoDB row-lock counters are global deltas for the isolated benchmark scenario.");
        System.out.println("scenario,round,concurrency,samples,wallMs,throughputPerSec,"
                + "p50Ms,p95Ms,p99Ms,p99_9Ms,innodbRowLockWaitsDelta,"
                + "innodbRowLockTimeMsDelta,innodbAverageWaitMs");

        runScenario("same-owner-multiple-families", true);
        runScenario("distinct-owners", false);
    }

    private void runScenario(String name, boolean sharedOwner) throws Exception {
        int totalSamples = WARMUP_SAMPLES + MEASURED_ROUNDS * SAMPLES_PER_ROUND;
        Scenario scenario = seedScenario(name, sharedOwner, totalSamples);
        try {
            runBatch(scenario.samples().subList(0, WARMUP_SAMPLES), CONCURRENCY);

            var aggregateLatencies = new ArrayList<Long>(MEASURED_ROUNDS * SAMPLES_PER_ROUND);
            long aggregateWallNanos = 0;
            long aggregateLockWaits = 0;
            long aggregateLockWaitTimeMs = 0;

            for (int round = 1; round <= MEASURED_ROUNDS; round++) {
                int from = WARMUP_SAMPLES + (round - 1) * SAMPLES_PER_ROUND;
                var roundSamples = scenario.samples().subList(from, from + SAMPLES_PER_ROUND);
                long waitsBefore = innodbStatus("Innodb_row_lock_waits");
                long waitTimeBefore = innodbStatus("Innodb_row_lock_time");

                BatchResult result = runBatch(roundSamples, CONCURRENCY);

                long lockWaits = innodbStatus("Innodb_row_lock_waits") - waitsBefore;
                long lockWaitTimeMs = innodbStatus("Innodb_row_lock_time") - waitTimeBefore;
                aggregateLatencies.addAll(result.latenciesNanos());
                aggregateWallNanos += result.wallNanos();
                aggregateLockWaits += lockWaits;
                aggregateLockWaitTimeMs += lockWaitTimeMs;

                printMeasurement(name, Integer.toString(round), result.latenciesNanos(),
                        result.wallNanos(), lockWaits, lockWaitTimeMs);
            }

            assertScenarioIntegrity(scenario, totalSamples);
            printMeasurement(name, "aggregate", aggregateLatencies, aggregateWallNanos,
                    aggregateLockWaits, aggregateLockWaitTimeMs);
        } finally {
            cleanupScenario(scenario);
        }
    }

    private Scenario seedScenario(String name, boolean sharedOwner, int sampleCount) {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        return transaction.execute(status -> {
            var ownerIds = new ArrayList<UUID>(sharedOwner ? 1 : sampleCount);
            var samples = new ArrayList<RefreshSample>(sampleCount);
            Instant now = Instant.now();
            UUID sharedOwnerId = null;

            if (sharedOwner) {
                sharedOwnerId = UUID.randomUUID();
                persistUser(sharedOwnerId, now);
                ownerIds.add(sharedOwnerId);
            }

            for (int index = 0; index < sampleCount; index++) {
                UUID ownerId;
                if (sharedOwner) {
                    ownerId = sharedOwnerId;
                } else {
                    ownerId = UUID.randomUUID();
                    persistUser(ownerId, now);
                    ownerIds.add(ownerId);
                }

                var generated = refreshTokenCodec.generate();
                UUID familyId = UUID.randomUUID();
                entityManager.persist(new RefreshToken(
                        UUID.randomUUID(),
                        ownerId,
                        familyId,
                        generated.hash(),
                        now.plusSeconds(86_400),
                        now,
                        null
                ));
                samples.add(new RefreshSample(generated.value()));

                if ((index + 1) % INSERT_BATCH_SIZE == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }
            entityManager.flush();
            return new Scenario(name, List.copyOf(ownerIds), List.copyOf(samples));
        });
    }

    private void persistUser(UUID id, Instant now) {
        entityManager.persist(new User(
                id,
                "bench-" + id,
                id + "@benchmark.invalid",
                BENCHMARK_PASSWORD,
                now,
                now
        ));
    }

    private BatchResult runBatch(List<RefreshSample> samples, int concurrency) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        var ready = new CountDownLatch(concurrency);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<List<Long>>>(concurrency);

        try {
            int baseSize = samples.size() / concurrency;
            int remainder = samples.size() % concurrency;
            int cursor = 0;

            for (int worker = 0; worker < concurrency; worker++) {
                int size = baseSize + (worker < remainder ? 1 : 0);
                int from = cursor;
                int to = from + size;
                cursor = to;

                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for benchmark start");
                    }

                    var elapsedSamples = new ArrayList<Long>(to - from);
                    for (int index = from; index < to; index++) {
                        long startedAt = System.nanoTime();
                        var session = authenticationService.refresh(samples.get(index).tokenValue());
                        long elapsedNanos = System.nanoTime() - startedAt;
                        if (session.refreshToken() == null || session.refreshToken().isBlank()) {
                            throw new IllegalStateException("Refresh completed without issuing a descendant token");
                        }
                        elapsedSamples.add(elapsedNanos);
                    }
                    return elapsedSamples;
                }));
            }

            assertTrue(ready.await(30, TimeUnit.SECONDS), "All benchmark workers must be ready");
            long startedAt = System.nanoTime();
            start.countDown();

            var latencies = new ArrayList<Long>(samples.size());
            for (Future<List<Long>> future : futures) {
                latencies.addAll(future.get(180, TimeUnit.SECONDS));
            }
            assertEquals(samples.size(), latencies.size(), "Every seeded token must be refreshed exactly once");
            return new BatchResult(List.copyOf(latencies), System.nanoTime() - startedAt);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS),
                    "Benchmark worker threads must terminate");
        }
    }

    private void assertScenarioIntegrity(Scenario scenario, int expectedRefreshes) {
        long tokenCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_refresh_token", Long.class);
        long revokedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_refresh_token WHERE revoked_at IS NOT NULL", Long.class);
        long activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_refresh_token WHERE revoked_at IS NULL", Long.class);
        long familyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT family_id) FROM tb_refresh_token", Long.class);
        long userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_user", Long.class);
        long malformedFamilies = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT family_id
                    FROM tb_refresh_token
                    GROUP BY family_id
                    HAVING COUNT(*) <> 2
                        OR SUM(revoked_at IS NOT NULL) <> 1
                        OR SUM(revoked_at IS NULL) <> 1
                ) invalid_families
                """, Long.class);

        assertEquals(expectedRefreshes * 2L, tokenCount,
                scenario.name() + " must persist one original and one descendant per family");
        assertEquals(expectedRefreshes, revokedCount,
                scenario.name() + " must revoke every original token");
        assertEquals(expectedRefreshes, activeCount,
                scenario.name() + " must leave exactly one active descendant per family");
        assertEquals(expectedRefreshes, familyCount,
                scenario.name() + " must keep each refresh in its own session family");
        assertEquals(scenario.ownerIds().size(), userCount,
                scenario.name() + " must persist the expected synthetic owners");
        assertEquals(0L, malformedFamilies,
                scenario.name() + " must preserve exactly one revoked and one active token per family");
    }

    private void cleanupScenario(Scenario scenario) {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> {
            for (int from = 0; from < scenario.ownerIds().size(); from += CLEANUP_BATCH_SIZE) {
                var ownerBatch = scenario.ownerIds().subList(
                        from, Math.min(from + CLEANUP_BATCH_SIZE, scenario.ownerIds().size()));
                entityManager.createQuery("delete from RefreshToken token where token.userId in :ownerIds")
                        .setParameter("ownerIds", ownerBatch)
                        .executeUpdate();
                entityManager.createQuery("delete from User user where user.id in :ownerIds")
                        .setParameter("ownerIds", ownerBatch)
                        .executeUpdate();
            }
        });
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_refresh_token", Long.class));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_user", Long.class));
    }

    private long innodbStatus(String variable) {
        String query = switch (variable) {
            case "Innodb_row_lock_waits" -> "SHOW GLOBAL STATUS LIKE 'Innodb_row_lock_waits'";
            case "Innodb_row_lock_time" -> "SHOW GLOBAL STATUS LIKE 'Innodb_row_lock_time'";
            default -> throw new IllegalArgumentException("Unsupported InnoDB status variable");
        };
        return jdbcTemplate.queryForObject(query, (resultSet, rowNumber) -> resultSet.getLong(2));
    }

    private void printMeasurement(String scenario, String round, List<Long> latenciesNanos,
                                  long wallNanos, long rowLockWaits, long rowLockWaitTimeMs) {
        Percentiles percentiles = percentiles(latenciesNanos);
        double throughput = latenciesNanos.size() / (wallNanos / 1_000_000_000.0);
        double averageLockWaitMs = rowLockWaits == 0 ? 0.0 : rowLockWaitTimeMs / (double) rowLockWaits;

        System.out.printf(Locale.ROOT,
                "%s,%s,%d,%d,%.3f,%.1f,%.3f,%.3f,%.3f,%.3f,%d,%d,%.3f%n",
                scenario,
                round,
                CONCURRENCY,
                latenciesNanos.size(),
                wallNanos / 1_000_000.0,
                throughput,
                percentiles.p50Ms(),
                percentiles.p95Ms(),
                percentiles.p99Ms(),
                percentiles.p99_9Ms(),
                rowLockWaits,
                rowLockWaitTimeMs,
                averageLockWaitMs);
    }

    private Percentiles percentiles(List<Long> samples) {
        var sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        return new Percentiles(
                percentileMs(sorted, 0.50),
                percentileMs(sorted, 0.95),
                percentileMs(sorted, 0.99),
                percentileMs(sorted, 0.999)
        );
    }

    private double percentileMs(List<Long> sorted, double percentile) {
        int rank = (int) Math.ceil(percentile * sorted.size());
        int index = Math.max(0, Math.min(sorted.size() - 1, rank - 1));
        return sorted.get(index) / 1_000_000.0;
    }

    private record RefreshSample(String tokenValue) {
    }

    private record Scenario(String name, List<UUID> ownerIds, List<RefreshSample> samples) {
    }

    private record BatchResult(List<Long> latenciesNanos, long wallNanos) {
    }

    private record Percentiles(double p50Ms, double p95Ms, double p99Ms, double p99_9Ms) {
    }
}
