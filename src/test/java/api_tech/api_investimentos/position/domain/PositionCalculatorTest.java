package api_tech.api_investimentos.position.domain;

import api_tech.api_investimentos.transaction.domain.InvestmentTransaction;
import api_tech.api_investimentos.transaction.domain.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PositionCalculatorTest {
    private static final UUID PORTFOLIO_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-08T12:00:00Z");

    @Test
    void shouldCalculateWeightedAverageIncludingAcquisitionFees() {
        var positions = PositionCalculator.calculate(List.of(
                transaction(TransactionType.BUY, "10", "20", "2"),
                transaction(TransactionType.BUY, "5", "30", "3")
        ));

        assertEquals(1, positions.size());
        assertPosition(positions.getFirst(), "15.00000000", "23.66666667", "355.00000000");
    }

    @Test
    void shouldPreserveAveragePriceOnPartialSale() {
        var positions = PositionCalculator.calculate(List.of(
                transaction(TransactionType.BUY, "10", "20", "0"),
                transaction(TransactionType.SELL, "4", "25", "9")
        ));

        assertPosition(positions.getFirst(), "6.00000000", "20.00000000", "120.00000000");
    }

    @Test
    void shouldRemoveZeroedPositionAndResetCostForANewCycle() {
        var positions = PositionCalculator.calculate(List.of(
                transaction(TransactionType.BUY, "2", "10", "2"),
                transaction(TransactionType.SELL, "2", "12", "1"),
                transaction(TransactionType.BUY, "3", "7", "0")
        ));

        assertPosition(positions.getFirst(), "3.00000000", "7.00000000", "21.00000000");
        assertEquals(List.of(), PositionCalculator.calculate(List.of(
                transaction(TransactionType.BUY, "2", "10", "0"),
                transaction(TransactionType.SELL, "2", "12", "0")
        )));
    }

    @Test
    void shouldKeepDecimalPrecisionAcrossRepeatedOperations() {
        var positions = PositionCalculator.calculate(List.of(
                transaction(TransactionType.BUY, "0.12345678", "31.12345678", "0.01"),
                transaction(TransactionType.BUY, "0.87654322", "32.87654321", "0.02"),
                transaction(TransactionType.SELL, "0.33333333", "35.00000000", "0.01")
        ));

        assertPosition(positions.getFirst(), "0.66666667", "32.69011280", "21.79340864");
    }

    @Test
    void shouldRejectAnInconsistentHistoryInsteadOfPublishingANegativePosition() {
        var history = List.of(transaction(TransactionType.SELL, "1", "10", "0"));

        assertThrows(IllegalArgumentException.class, () -> PositionCalculator.calculate(history));
    }

    private void assertPosition(Position position, String quantity, String averagePrice, String totalCost) {
        assertEquals(ASSET_ID, position.assetId());
        assertEquals(new BigDecimal(quantity), position.quantity());
        assertEquals(new BigDecimal(averagePrice), position.averagePrice());
        assertEquals(new BigDecimal(totalCost), position.totalCost());
    }

    private InvestmentTransaction transaction(TransactionType type, String quantity, String unitPrice, String fees) {
        return new InvestmentTransaction(UUID.randomUUID(), UUID.randomUUID(), PORTFOLIO_ID, ASSET_ID, type,
                new BigDecimal(quantity), new BigDecimal(unitPrice), new BigDecimal(fees), OCCURRED_AT);
    }
}
