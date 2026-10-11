package api_tech.api_investimentos.position.domain;

import api_tech.api_investimentos.transaction.domain.InvestmentTransaction;
import api_tech.api_investimentos.transaction.domain.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reproducible micro-benchmark for the in-memory part of the positions read path.
 * It is intentionally not named *Test so normal builds remain deterministic.
 */
class PositionCalculatorBenchmark {
    private static final UUID PORTFOLIO_ID = UUID.fromString("10000000-0000-0000-0000-000000000000");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-11T00:00:00Z");
    private static final int ASSET_COUNT = 20;
    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURED_ROUNDS = 9;

    @Test
    void measureFullLedgerCalculation() {
        System.out.printf("position-calculation java=%s processors=%d maxHeapMiB=%d%n",
                Runtime.version(), Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / (1024 * 1024));
        System.out.println("transactions,assets,medianMs,minMs,maxMs,transactionsPerSecond");

        for (int transactionCount : List.of(1_000, 10_000, 100_000)) {
            var ledger = ledger(transactionCount);
            for (int round = 0; round < WARMUP_ROUNDS; round++) {
                assertEquals(ASSET_COUNT, PositionCalculator.calculate(ledger).size());
            }

            long[] elapsedNanos = new long[MEASURED_ROUNDS];
            for (int round = 0; round < MEASURED_ROUNDS; round++) {
                long startedAt = System.nanoTime();
                var positions = PositionCalculator.calculate(ledger);
                elapsedNanos[round] = System.nanoTime() - startedAt;
                assertEquals(ASSET_COUNT, positions.size());
            }
            Arrays.sort(elapsedNanos);
            long median = elapsedNanos[MEASURED_ROUNDS / 2];
            double medianMs = median / 1_000_000.0;
            double throughput = transactionCount / (median / 1_000_000_000.0);
            System.out.printf("%d,%d,%.3f,%.3f,%.3f,%.0f%n", transactionCount, ASSET_COUNT,
                    medianMs, elapsedNanos[0] / 1_000_000.0,
                    elapsedNanos[MEASURED_ROUNDS - 1] / 1_000_000.0, throughput);
        }
    }

    private List<InvestmentTransaction> ledger(int transactionCount) {
        var assets = new UUID[ASSET_COUNT];
        for (int index = 0; index < ASSET_COUNT; index++) {
            assets[index] = UUID.nameUUIDFromBytes(("asset-" + index).getBytes());
        }

        var transactions = new ArrayList<InvestmentTransaction>(transactionCount);
        for (int index = 0; index < transactionCount; index++) {
            transactions.add(new InvestmentTransaction(
                    UUID.nameUUIDFromBytes(("transaction-" + index).getBytes()),
                    UUID.nameUUIDFromBytes(("request-" + index).getBytes()),
                    PORTFOLIO_ID,
                    assets[index % ASSET_COUNT],
                    TransactionType.BUY,
                    new BigDecimal("1.00000000"),
                    new BigDecimal("100.00000000"),
                    new BigDecimal("0.10"),
                    OCCURRED_AT.plusSeconds(index)
            ));
        }
        return List.copyOf(transactions);
    }
}
