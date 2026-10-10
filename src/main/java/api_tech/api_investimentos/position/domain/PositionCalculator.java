package api_tech.api_investimentos.position.domain;

import api_tech.api_investimentos.transaction.domain.InvestmentTransaction;
import api_tech.api_investimentos.transaction.domain.TransactionType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PositionCalculator {
    private static final int SCALE = 8;
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private PositionCalculator() {
    }

    public static List<Position> calculate(List<InvestmentTransaction> transactions) {
        Map<UUID, MutablePosition> positions = new LinkedHashMap<>();
        for (InvestmentTransaction transaction : transactions) {
            positions.computeIfAbsent(transaction.getAssetId(), ignored -> new MutablePosition())
                    .apply(transaction);
        }

        var result = new ArrayList<Position>();
        positions.forEach((assetId, position) -> {
            if (position.quantity.signum() > 0) {
                result.add(position.snapshot(assetId));
            }
        });
        return List.copyOf(result);
    }

    private static final class MutablePosition {
        private BigDecimal quantity = ZERO;
        private BigDecimal totalCost = ZERO;

        private void apply(InvestmentTransaction transaction) {
            if (transaction.getType() == TransactionType.BUY) {
                applyBuy(transaction);
            } else {
                applySell(transaction);
            }
        }

        private void applyBuy(InvestmentTransaction transaction) {
            BigDecimal acquisitionCost = transaction.getQuantity()
                    .multiply(transaction.getUnitPrice(), CALCULATION_CONTEXT)
                    .add(transaction.getFees(), CALCULATION_CONTEXT);
            quantity = quantity.add(transaction.getQuantity(), CALCULATION_CONTEXT);
            totalCost = totalCost.add(acquisitionCost, CALCULATION_CONTEXT);
        }

        private void applySell(InvestmentTransaction transaction) {
            BigDecimal resultingQuantity = quantity.subtract(transaction.getQuantity(), CALCULATION_CONTEXT);
            if (resultingQuantity.signum() < 0) {
                throw new IllegalArgumentException("Transaction history produces a negative position");
            }
            if (resultingQuantity.signum() == 0) {
                quantity = ZERO;
                totalCost = ZERO;
                return;
            }

            BigDecimal averagePrice = totalCost.divide(quantity, SCALE, RoundingMode.HALF_EVEN);
            quantity = resultingQuantity;
            totalCost = averagePrice.multiply(resultingQuantity, CALCULATION_CONTEXT);
        }

        private Position snapshot(UUID assetId) {
            BigDecimal normalizedQuantity = quantity.setScale(SCALE, RoundingMode.HALF_EVEN);
            BigDecimal normalizedCost = totalCost.setScale(SCALE, RoundingMode.HALF_EVEN);
            BigDecimal averagePrice = normalizedCost.divide(normalizedQuantity, SCALE, RoundingMode.HALF_EVEN);
            return new Position(assetId, normalizedQuantity, averagePrice, normalizedCost);
        }
    }
}
