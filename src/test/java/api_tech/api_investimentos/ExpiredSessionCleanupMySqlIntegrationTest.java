package api_tech.api_investimentos;

import api_tech.api_investimentos.identity.application.ExpiredSessionCleanupService;
import api_tech.api_investimentos.identity.application.AuthenticatedSession;
import api_tech.api_investimentos.identity.application.AuthenticationService;
import api_tech.api_investimentos.identity.application.InvalidRefreshTokenException;
import api_tech.api_investimentos.identity.application.RefreshTokenCodec;
import api_tech.api_investimentos.identity.application.RefreshTokenRepository;
import api_tech.api_investimentos.identity.application.UserRepository;
import api_tech.api_investimentos.identity.domain.RefreshToken;
import api_tech.api_investimentos.identity.infrastructure.JpaRefreshTokenRepository;
import api_tech.api_investimentos.identity.domain.User;
import api_tech.api_investimentos.asset.application.AssetRepository;
import api_tech.api_investimentos.asset.domain.Asset;
import api_tech.api_investimentos.asset.domain.AssetMarket;
import api_tech.api_investimentos.asset.domain.AssetType;
import api_tech.api_investimentos.portfolio.application.PortfolioNotFoundException;
import api_tech.api_investimentos.portfolio.application.PortfolioRepository;
import api_tech.api_investimentos.portfolio.application.PortfolioService;
import api_tech.api_investimentos.portfolio.domain.Portfolio;
import api_tech.api_investimentos.position.application.PositionService;
import api_tech.api_investimentos.transaction.application.CreateTransactionCommand;
import api_tech.api_investimentos.transaction.application.CreateTransactionResult;
import api_tech.api_investimentos.transaction.application.IdempotencyConflictException;
import api_tech.api_investimentos.transaction.application.TransactionRepository;
import api_tech.api_investimentos.transaction.application.TransactionService;
import api_tech.api_investimentos.transaction.domain.InvestmentTransaction;
import api_tech.api_investimentos.transaction.domain.TransactionType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
@Import({ExpiredSessionCleanupMySqlIntegrationTest.FixedClockConfig.class,
        ExpiredSessionCleanupMySqlIntegrationTest.CoordinatedRepositoryConfig.class})
@TestPropertySource(properties = {
        "security.refresh-token.cleanup.enabled=false",
        "security.refresh-token.cleanup.retention=P7D",
        "security.refresh-token.cleanup.batch-size=10"
})
class ExpiredSessionCleanupMySqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-27T20:00:00Z");
    private static final String MYSQL_IMAGE =
            "mysql:8.4@sha256:6ea90827b1100f8f2ae306a539f86d2c264a26ed435a2a9f75551dd5c3aeb242";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("apiinvestimentos")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private ExpiredSessionCleanupService cleanupService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private PositionService positionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private RefreshTokenCodec refreshTokenCodec;

    @Autowired
    private FamilyReadGate familyReadGate;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Test
    void shouldApplyMigrationsAndCleanOnlyFullyExpiredFamilies() {
        UUID userId = saveUser();
        UUID expiredFamily = UUID.randomUUID();
        saveToken(userId, expiredFamily, NOW.minusSeconds(15 * 24 * 60 * 60L));
        saveToken(userId, expiredFamily, NOW.minusSeconds(14 * 24 * 60 * 60L));

        UUID activeFamily = UUID.randomUUID();
        saveToken(userId, activeFamily, NOW.minusSeconds(30 * 24 * 60 * 60L));
        saveToken(userId, activeFamily, NOW.plusSeconds(24 * 60 * 60L));

        assertEquals("10", latestFlywayVersion());
        assertEquals(2, cleanupIndexColumnCount());
        assertEquals(2, ledgerIndexColumnCount());
        assertEquals(2, cleanupService.cleanupBatch());
        assertEquals(2, tokenCount());
        assertEquals(0, cleanupService.cleanupBatch());
    }

    @Test
    void shouldCleanCommittedExpiredSessionsSafelyUnderConcurrentExecutions() throws Exception {
        inCommittedTransaction(() -> {
            UUID userId = saveUser();
            UUID firstFamily = UUID.randomUUID();
            saveToken(userId, firstFamily, NOW.minusSeconds(30 * 24 * 60 * 60L));
            saveToken(userId, firstFamily, NOW.minusSeconds(29 * 24 * 60 * 60L));
            saveToken(userId, UUID.randomUUID(), NOW.minusSeconds(20 * 24 * 60 * 60L));
            entityManager.flush();
        });

        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var firstCleanup = executor.submit(() -> {
                start.await();
                return cleanupService.cleanupBatch();
            });
            var secondCleanup = executor.submit(() -> {
                start.await();
                return cleanupService.cleanupBatch();
            });

            start.countDown();
            assertEquals(3,
                    firstCleanup.get(15, TimeUnit.SECONDS) + secondCleanup.get(15, TimeUnit.SECONDS));
            assertEquals(0, tokenCount());
            assertEquals(0, cleanupService.cleanupBatch());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRevokeTheDescendantAfterConcurrentRefreshTokenReuseOnMySql() throws Exception {
        var fixtureTransaction = new TransactionTemplate(transactionManager);
        fixtureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var original = refreshTokenCodec.generate();
        UUID familyId = UUID.randomUUID();
        UUID ownerId = fixtureTransaction.execute(status -> {
            UUID id = saveUser();
            Instant now = Instant.now();
            refreshTokenRepository.save(new RefreshToken(UUID.randomUUID(), id, familyId,
                    original.hash(), now.plusSeconds(3600), now, null));
            return id;
        });
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> refreshConcurrently(original.value(), ready, start));
            var second = executor.submit(() -> refreshConcurrently(original.value(), ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            var a = first.get(15, TimeUnit.SECONDS);
            var b = second.get(15, TimeUnit.SECONDS);
            assertTrue(a.isPresent() ^ b.isPresent(), "Exactly one refresh must succeed");
            var winner = a.or(() -> b).orElseThrow();
            fixtureTransaction.executeWithoutResult(status -> {
                var tokens = refreshTokenRepository.findAllByFamilyId(familyId);
                assertEquals(2, tokens.size(), "Only one descendant must be persisted");
                assertTrue(tokens.stream().allMatch(RefreshToken::isRevoked),
                        "Reuse must revoke both the original and its descendant");
                assertTrue(tokens.stream().anyMatch(token ->
                        token.getTokenHash().equals(refreshTokenCodec.hash(winner.refreshToken()))));
            });
            assertThrows(InvalidRefreshTokenException.class,
                    () -> fixtureTransaction.execute(status ->
                            authenticationService.refresh(winner.refreshToken())));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
            inCommittedTransaction(() -> {
                deleteFixture("delete from RefreshToken t where t.familyId = :id", familyId);
                deleteFixture("delete from User u where u.id = :id", ownerId);
            });
        }
    }


    @Test
    void shouldAllowRefreshForDifferentUserWhileAnotherOwnerIsLockedOnMySql() throws Exception {
        var fixtureTransaction = new TransactionTemplate(transactionManager);
        fixtureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var firstToken = refreshTokenCodec.generate();
        var secondToken = refreshTokenCodec.generate();
        UUID firstFamilyId = UUID.randomUUID();
        UUID secondFamilyId = UUID.randomUUID();
        UUID[] ownerIds = fixtureTransaction.execute(status -> {
            UUID firstOwnerId = saveUser();
            UUID secondOwnerId = saveUser();
            Instant now = Instant.now();
            refreshTokenRepository.save(new RefreshToken(UUID.randomUUID(), firstOwnerId, firstFamilyId,
                    firstToken.hash(), now.plusSeconds(3600), now, null));
            refreshTokenRepository.save(new RefreshToken(UUID.randomUUID(), secondOwnerId, secondFamilyId,
                    secondToken.hash(), now.plusSeconds(3600), now, null));
            return new UUID[]{firstOwnerId, secondOwnerId};
        });

        familyReadGate.arm(firstFamilyId, secondToken.hash());
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<AuthenticatedSession> firstRefresh =
                    executor.submit(() -> authenticationService.refresh(firstToken.value()));
            assertTrue(familyReadGate.awaitFamilyRead(10, TimeUnit.SECONDS),
                    "The first refresh must hold its owner's lock while paused after the family read");

            Future<AuthenticatedSession> secondRefresh =
                    executor.submit(() -> authenticationService.refresh(secondToken.value()));
            assertTrue(familyReadGate.awaitTokenLookup(10, TimeUnit.SECONDS),
                    "The second user's token lookup must complete while the first owner remains locked");

            AuthenticatedSession secondSession = secondRefresh.get(10, TimeUnit.SECONDS);
            assertTrue(!firstRefresh.isDone(),
                    "The first refresh must remain paused while the independent owner completes");

            familyReadGate.releaseFamilyRead();
            AuthenticatedSession firstSession = firstRefresh.get(15, TimeUnit.SECONDS);

            var firstFamily = fixtureTransaction.execute(
                    status -> refreshTokenRepository.findAllByFamilyId(firstFamilyId));
            var secondFamily = fixtureTransaction.execute(
                    status -> refreshTokenRepository.findAllByFamilyId(secondFamilyId));
            assertEquals(2, firstFamily.size());
            assertEquals(2, secondFamily.size());
            assertEquals(1L, firstFamily.stream().filter(RefreshToken::isRevoked).count());
            assertEquals(1L, secondFamily.stream().filter(RefreshToken::isRevoked).count());
            assertTrue(firstFamily.stream().anyMatch(token ->
                    token.getTokenHash().equals(refreshTokenCodec.hash(firstSession.refreshToken()))
                            && token.getUserId().equals(ownerIds[0]) && !token.isRevoked()));
            assertTrue(secondFamily.stream().anyMatch(token ->
                    token.getTokenHash().equals(refreshTokenCodec.hash(secondSession.refreshToken()))
                            && token.getUserId().equals(ownerIds[1]) && !token.isRevoked()));
        } finally {
            familyReadGate.releaseFamilyRead();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
            inCommittedTransaction(() -> {
                deleteFixture("delete from RefreshToken t where t.familyId = :id", firstFamilyId);
                deleteFixture("delete from RefreshToken t where t.familyId = :id", secondFamilyId);
                deleteFixture("delete from User u where u.id = :id", ownerIds[0]);
                deleteFixture("delete from User u where u.id = :id", ownerIds[1]);
            });
        }
    }

    @ParameterizedTest(name = "Revocation starts first: {0}")
    @ValueSource(booleans = {true, false})
    void shouldSerializeRefreshAgainstRevocationInBothOrderingsOnMySql(boolean revokeFirst) throws Exception {
        var fixtureTransaction = new TransactionTemplate(transactionManager);
        fixtureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var ancestor = refreshTokenCodec.generate();
        var descendant = refreshTokenCodec.generate();
        UUID familyId = UUID.randomUUID();
        UUID ownerId = fixtureTransaction.execute(status -> {
            UUID id = saveUser();
            Instant now = Instant.now();
            refreshTokenRepository.save(new RefreshToken(UUID.randomUUID(), id, familyId,
                    ancestor.hash(), now.plusSeconds(3600), now, null));
            refreshTokenRepository.save(new RefreshToken(UUID.randomUUID(), id, familyId,
                    descendant.hash(), now.plusSeconds(3600), now, null));
            return id;
        });

        familyReadGate.arm(familyId, revokeFirst ? descendant.hash() : ancestor.hash());
        var executor = Executors.newFixedThreadPool(2);
        Future<?> revocation;
        Future<Optional<AuthenticatedSession>> refresh;
        try {
            if (revokeFirst) {
                revocation = executor.submit(() -> authenticationService.revoke(ancestor.value()));
                assertTrue(familyReadGate.awaitFamilyRead(10, TimeUnit.SECONDS),
                        "Revocation must pause after locking the family");
                refresh = executor.submit(() -> refreshIfAccepted(descendant.value()));
            } else {
                refresh = executor.submit(() -> refreshIfAccepted(descendant.value()));
                assertTrue(familyReadGate.awaitFamilyRead(10, TimeUnit.SECONDS),
                        "Refresh must pause after locking the family");
                revocation = executor.submit(() -> authenticationService.revoke(ancestor.value()));
            }

            assertTrue(familyReadGate.awaitTokenLookup(10, TimeUnit.SECONDS),
                    "The competing operation must resolve its distinct token while the first holds the family lock");
            familyReadGate.releaseFamilyRead();

            var issued = refresh.get(15, TimeUnit.SECONDS);
            assertEquals(!revokeFirst, issued.isPresent(),
                    "Only a refresh that obtains the family lock before revocation may issue a descendant");
            revocation.get(15, TimeUnit.SECONDS);

            var persisted = fixtureTransaction.execute(
                    status -> refreshTokenRepository.findAllByFamilyId(familyId));
            assertEquals(revokeFirst ? 2 : 3, persisted.size(),
                    "No descendant may be issued after revocation wins the family lock");
            assertTrue(persisted.stream().allMatch(RefreshToken::isRevoked),
                    "Every persisted token in the family must be revoked");
            assertThrows(InvalidRefreshTokenException.class, () -> fixtureTransaction.execute(
                    status -> authenticationService.refresh(ancestor.value())));
            assertThrows(InvalidRefreshTokenException.class, () -> fixtureTransaction.execute(
                    status -> authenticationService.refresh(descendant.value())));
            issued.ifPresent(session -> assertThrows(InvalidRefreshTokenException.class, () ->
                    fixtureTransaction.execute(status -> authenticationService.refresh(session.refreshToken()))));
        } finally {
            familyReadGate.releaseFamilyRead();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
            inCommittedTransaction(() -> {
                deleteFixture("delete from RefreshToken t where t.familyId = :id", familyId);
                deleteFixture("delete from User u where u.id = :id", ownerId);
            });
        }
    }

    private Optional<AuthenticatedSession> refreshIfAccepted(String token) {
        try {
            return Optional.of(authenticationService.refresh(token));
        } catch (InvalidRefreshTokenException invalid) {
            return Optional.empty();
        }
    }

    private Optional<AuthenticatedSession> refreshConcurrently(
            String token, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
        try {
            return Optional.of(authenticationService.refresh(token));
        } catch (InvalidRefreshTokenException invalid) {
            return Optional.empty();
        }
    }

    @ParameterizedTest(name = "Concurrent replay with different payload: {0}")
    @ValueSource(booleans = {false, true})
    void shouldPersistOnlyOneTransactionForConcurrentRequestsWithSameKeyOnMySql(boolean differentPayload)
            throws Exception {
        var fixtureTransaction = new TransactionTemplate(transactionManager);
        fixtureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        UUID portfolioId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID ownerId = fixtureTransaction.execute(status -> {
            UUID id = saveUser();
            portfolioRepository.save(new Portfolio(portfolioId, id, "Concurrent replay portfolio"));
            assetRepository.save(new Asset(assetId, AssetMarket.B3, "REPLAY3", AssetType.STOCK, "Replay asset"));
            return id;
        });
        var command = new CreateTransactionCommand(UUID.randomUUID(), ownerId, portfolioId, assetId,
                TransactionType.BUY, new BigDecimal("2"), new BigDecimal("20"), BigDecimal.ZERO, NOW);
        var replay = differentPayload
                ? new CreateTransactionCommand(command.requestId(), ownerId, portfolioId, assetId,
                        TransactionType.BUY, new BigDecimal("3"), command.unitPrice(), command.fees(), NOW)
                : command;
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> submitTransaction(command, ready, start));
            var second = executor.submit(() -> submitTransaction(replay, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            var a = first.get(15, TimeUnit.SECONDS);
            var b = second.get(15, TimeUnit.SECONDS);
            if (differentPayload) {
                assertTrue(a.isEmpty() ^ b.isEmpty(), "Exactly one request must fail with an idempotency conflict");
            } else {
                assertEquals(a.orElseThrow().transaction().getId(), b.orElseThrow().transaction().getId());
            }
            boolean firstCreated = a.map(CreateTransactionResult::created).orElse(false);
            assertTrue(firstCreated ^ b.map(CreateTransactionResult::created).orElse(false),
                    "Exactly one request must create a transaction");
            var winner = (firstCreated ? a : b).orElseThrow().transaction();
            var expectedQuantity = (firstCreated ? command : replay).quantity();
            var persisted = fixtureTransaction.execute(status -> entityManager.createQuery(
                            "select t from InvestmentTransaction t where t.portfolioId = :id", InvestmentTransaction.class)
                    .setParameter("id", portfolioId).getResultList());
            assertEquals(1, persisted.size());
            assertEquals(winner.getId(), persisted.getFirst().getId());
            assertEquals(0, expectedQuantity.compareTo(persisted.getFirst().getQuantity()));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
            inCommittedTransaction(() -> {
                deleteFixture("delete from InvestmentTransaction t where t.portfolioId = :id", portfolioId);
                deleteFixture("delete from Portfolio p where p.id = :id", portfolioId);
                deleteFixture("delete from Asset a where a.id = :id", assetId);
                deleteFixture("delete from User u where u.id = :id", ownerId);
            });
        }
    }

    private Optional<CreateTransactionResult> submitTransaction(
            CreateTransactionCommand command, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
        try {
            return Optional.of(transactionService.create(command));
        } catch (IdempotencyConflictException conflict) {
            return Optional.empty();
        }
    }

    private void deleteFixture(String query, UUID id) {
        entityManager.createQuery(query).setParameter("id", id).executeUpdate();
    }

    @Test
    void shouldEnforcePortfolioAndTransactionOwnershipOnMySql() {
        UUID ownerId = saveUser();
        UUID strangerId = saveUser();
        UUID portfolioId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        portfolioRepository.save(new Portfolio(portfolioId, ownerId, "Owner portfolio"));
        assetRepository.save(new Asset(assetId, AssetMarket.B3, "OWNER3", AssetType.STOCK, "Owner asset"));
        entityManager.flush();

        assertEquals(1L, portfolioService.listByOwner(ownerId, 0, 20).totalElements());
        assertEquals(0L, portfolioService.listByOwner(strangerId, 0, 20).totalElements());
        assertEquals(portfolioId, portfolioService.getById(portfolioId, ownerId).getId());
        assertThrows(PortfolioNotFoundException.class,
                () -> portfolioService.getById(portfolioId, strangerId));

        var ownedCommand = new CreateTransactionCommand(UUID.randomUUID(), ownerId, portfolioId, assetId,
                TransactionType.BUY, new BigDecimal("2"), new BigDecimal("20"), BigDecimal.ZERO, NOW);
        assertTrue(transactionService.create(ownedCommand).created());
        assertEquals(1L, transactionService.listByPortfolio(portfolioId, ownerId, 0, 20).totalElements());
        assertThrows(PortfolioNotFoundException.class,
                () -> transactionService.listByPortfolio(portfolioId, strangerId, 0, 20));

        var foreignCommand = new CreateTransactionCommand(UUID.randomUUID(), strangerId, portfolioId, assetId,
                TransactionType.BUY, BigDecimal.ONE, new BigDecimal("20"), BigDecimal.ZERO, NOW);
        assertThrows(PortfolioNotFoundException.class, () -> transactionService.create(foreignCommand));
        assertEquals(1, transactionRepository.findAllByPortfolioIdInLedgerOrder(portfolioId).size());
    }

    @Test
    void shouldDerivePositionsInMonotonicLedgerOrderAndIsolateTheOwner() {
        UUID ownerId = saveUser();
        UUID strangerId = saveUser();
        UUID portfolioId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        portfolioRepository.save(new Portfolio(portfolioId, ownerId, "MySQL portfolio"));
        assetRepository.save(new Asset(assetId, AssetMarket.B3, "MYSQL3", AssetType.STOCK, "MySQL asset"));

        saveTransaction(portfolioId, assetId, TransactionType.BUY, "10", "20", "2", NOW.plusSeconds(60));
        saveTransaction(portfolioId, assetId, TransactionType.SELL, "10", "25", "1", NOW.minusSeconds(60));
        saveTransaction(portfolioId, assetId, TransactionType.BUY, "5", "30", "3", NOW);

        var ledgerOrders = jdbcTemplate.queryForList(
                "SELECT ledger_order FROM investment_transaction ORDER BY ledger_order",
                Long.class
        );
        assertEquals(3, ledgerOrders.size());
        assertEquals(ledgerOrders.getFirst() + 1, ledgerOrders.get(1));
        assertEquals(ledgerOrders.get(1) + 1, ledgerOrders.get(2));

        var positions = positionService.listByPortfolio(portfolioId, ownerId);
        assertEquals(1, positions.size());
        assertEquals(assetId, positions.getFirst().assetId());
        assertEquals(new BigDecimal("5.00000000"), positions.getFirst().quantity());
        assertEquals(new BigDecimal("30.60000000"), positions.getFirst().averagePrice());
        assertEquals(new BigDecimal("153.00000000"), positions.getFirst().totalCost());
        assertThrows(PortfolioNotFoundException.class,
                () -> positionService.listByPortfolio(portfolioId, strangerId));
    }

    private UUID saveUser() {
        UUID id = UUID.randomUUID();
        var savedUser = userRepository.save(new User(
                id,
                "mysql-cleanup-user",
                "mysql-cleanup-" + id + "@example.com",
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                NOW,
                NOW
        ));
        entityManager.flush();
        return savedUser.getId();
    }

    private void saveToken(UUID userId, UUID familyId, Instant expiresAt) {
        String tokenHash = UUID.randomUUID().toString().replace("-", "").repeat(2);
        refreshTokenRepository.save(new RefreshToken(
                UUID.randomUUID(),
                userId,
                familyId,
                tokenHash,
                expiresAt,
                expiresAt.minusSeconds(60),
                null
        ));
    }

    private String latestFlywayVersion() {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE "
                        + "ORDER BY installed_rank DESC LIMIT 1",
                String.class
        );
    }

    private int cleanupIndexColumnCount() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'tb_refresh_token'
                  AND index_name = 'ix_refresh_token_family_expiry'
                """,
                Integer.class
        );
    }

    private int ledgerIndexColumnCount() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'investment_transaction'
                  AND index_name = 'ix_transaction_portfolio_ledger'
                """,
                Integer.class
        );
    }

    private void saveTransaction(UUID portfolioId, UUID assetId, TransactionType type,
                                 String quantity, String unitPrice, String fees, Instant occurredAt) {
        transactionRepository.save(new InvestmentTransaction(
                UUID.randomUUID(), UUID.randomUUID(), portfolioId, assetId, type,
                new BigDecimal(quantity), new BigDecimal(unitPrice), new BigDecimal(fees), occurredAt
        ));
    }

    private int tokenCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_refresh_token", Integer.class);
    }

    private void inCommittedTransaction(Runnable work) {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> work.run());
    }

    static final class FamilyReadGate {
        private volatile UUID targetFamilyId;
        private volatile String targetTokenHash;
        private volatile CountDownLatch familyReadEntered = new CountDownLatch(0);
        private volatile CountDownLatch allowFamilyRead = new CountDownLatch(0);
        private volatile CountDownLatch tokenLookupCompleted = new CountDownLatch(0);
        private final AtomicBoolean armed = new AtomicBoolean();

        void arm(UUID familyId, String tokenHash) {
            targetFamilyId = familyId;
            targetTokenHash = tokenHash;
            familyReadEntered = new CountDownLatch(1);
            allowFamilyRead = new CountDownLatch(1);
            tokenLookupCompleted = new CountDownLatch(1);
            armed.set(true);
        }

        void afterFamilyRead(UUID familyId) {
            if (!familyId.equals(targetFamilyId) || !armed.compareAndSet(true, false)) {
                return;
            }
            familyReadEntered.countDown();
            try {
                if (!allowFamilyRead.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release the family read gate");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while holding the family read gate", exception);
            }
        }

        void afterTokenLookup(String tokenHash) {
            if (tokenHash.equals(targetTokenHash)) {
                tokenLookupCompleted.countDown();
            }
        }

        boolean awaitFamilyRead(long timeout, TimeUnit unit) throws InterruptedException {
            return familyReadEntered.await(timeout, unit);
        }

        boolean awaitTokenLookup(long timeout, TimeUnit unit) throws InterruptedException {
            return tokenLookupCompleted.await(timeout, unit);
        }

        void releaseFamilyRead() {
            allowFamilyRead.countDown();
        }
    }

    @TestConfiguration
    static class CoordinatedRepositoryConfig {

        @Bean
        FamilyReadGate familyReadGate() {
            return new FamilyReadGate();
        }

        @Bean
        @Primary
        RefreshTokenRepository coordinatedRefreshTokenRepository(
                JpaRefreshTokenRepository delegate,
                FamilyReadGate gate
        ) {
            return new RefreshTokenRepository() {
                @Override
                public <S extends RefreshToken> S save(S refreshToken) {
                    return ((CrudRepository<RefreshToken, UUID>) delegate).save(refreshToken);
                }

                @Override
                public Optional<RefreshToken> findByTokenHash(String tokenHash) {
                    var token = delegate.findByTokenHash(tokenHash);
                    gate.afterTokenLookup(tokenHash);
                    return token;
                }

                @Override
                public Optional<UUID> findUserIdByTokenHash(String tokenHash) {
                    var userId = delegate.findUserIdByTokenHash(tokenHash);
                    gate.afterTokenLookup(tokenHash);
                    return userId;
                }

                @Override
                public Optional<UUID> findFamilyIdByTokenHash(String tokenHash) {
                    var familyId = delegate.findFamilyIdByTokenHash(tokenHash);
                    gate.afterTokenLookup(tokenHash);
                    return familyId;
                }

                @Override
                public List<RefreshToken> findAllByFamilyId(UUID familyId) {
                    var tokens = delegate.findAllByFamilyId(familyId);
                    gate.afterFamilyRead(familyId);
                    return tokens;
                }

                @Override
                public List<RefreshToken> findAllByFamilyIdForUpdate(UUID familyId) {
                    var tokens = delegate.findAllByFamilyIdForUpdate(familyId);
                    gate.afterFamilyRead(familyId);
                    return tokens;
                }
            };
        }
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
