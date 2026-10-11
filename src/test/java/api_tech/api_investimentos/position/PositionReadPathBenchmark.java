package api_tech.api_investimentos.position;

import api_tech.api_investimentos.position.domain.PositionCalculator;
import api_tech.api_investimentos.transaction.application.TransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.management.ManagementFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Measures database materialization plus calculation; excluded from normal test discovery by name. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:position-read-benchmark;MODE=MySQL;DB_CLOSE_DELAY=-1")
class PositionReadPathBenchmark {
    private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PORTFOLIO_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ASSET_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final int BATCH_SIZE = 1_000;

    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionRepository transactions;
    @Autowired EntityManager entityManager;

    @Test
    void measureDatabaseMaterializationAndCalculation() {
        seedReferences();
        System.out.printf("position-read-path java=%s processors=%d maxHeapMiB=%d%n",
                Runtime.version(), Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / (1024 * 1024));
        System.out.println("transactions,loadMs,calculateMs,heapDeltaMiB");

        int inserted = 0;
        for (int target : List.of(1_000, 10_000, 100_000)) {
            insertTransactions(inserted, target);
            inserted = target;
            entityManager.clear();
            settleHeap();
            long heapBefore = usedHeap();

            long loadStarted = System.nanoTime();
            var ledger = transactions.findAllByPortfolioIdInLedgerOrder(PORTFOLIO_ID);
            double loadMs = elapsedMillis(loadStarted);
            long heapAfterLoad = usedHeap();

            long calculationStarted = System.nanoTime();
            var positions = PositionCalculator.calculate(ledger);
            double calculationMs = elapsedMillis(calculationStarted);

            assertEquals(target, ledger.size());
            assertEquals(1, positions.size());
            System.out.printf("%d,%.3f,%.3f,%.3f%n", target, loadMs, calculationMs,
                    Math.max(0, heapAfterLoad - heapBefore) / (1024.0 * 1024.0));
        }
    }

    private void seedReferences() {
        jdbc.update("INSERT INTO tb_user (id, username, email, password, role) VALUES (?, 'benchmark', 'benchmark@example.com', 'not-used', 'USER')", OWNER_ID);
        jdbc.update("INSERT INTO portfolio (id, owner_id, name, created_at, updated_at) VALUES (?, ?, 'Benchmark', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", PORTFOLIO_ID, OWNER_ID);
        jdbc.update("INSERT INTO asset (id, market, ticker, type, name, created_at) VALUES (?, 'B3', 'BENCH3', 'STOCK', 'Benchmark asset', CURRENT_TIMESTAMP)", ASSET_ID);
    }

    private void insertTransactions(int fromInclusive, int toExclusive) {
        for (int batchStart = fromInclusive; batchStart < toExclusive; batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, toExclusive);
            var rows = new ArrayList<Object[]>(batchEnd - batchStart);
            for (int index = batchStart; index < batchEnd; index++) {
                rows.add(new Object[]{UUID.randomUUID(), UUID.randomUUID(), PORTFOLIO_ID, ASSET_ID,
                        "BUY", "1.00000000", "100.00000000", "0.10",
                        Timestamp.from(Instant.parse("2026-10-11T00:00:00Z").plusSeconds(index)),
                        Timestamp.from(Instant.parse("2026-10-11T00:00:00Z").plusSeconds(index))});
            }
            jdbc.batchUpdate("INSERT INTO investment_transaction (id, request_id, portfolio_id, asset_id, type, quantity, unit_price, fees, occurred_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);
        }
    }

    private void settleHeap() {
        System.gc();
        System.runFinalization();
    }

    private long usedHeap() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private double elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000.0;
    }
}
