package api_tech.api_investimentos.transaction.api;

import api_tech.api_investimentos.transaction.domain.InvestmentTransaction;
import api_tech.api_investimentos.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(UUID id, UUID requestId, UUID portfolioId, UUID assetId, TransactionType type,
                                  BigDecimal quantity, BigDecimal unitPrice, BigDecimal fees,
                                  Instant occurredAt, Instant createdAt) {
    static TransactionResponse from(InvestmentTransaction transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getRequestId(), transaction.getPortfolioId(),
                transaction.getAssetId(), transaction.getType(), transaction.getQuantity(), transaction.getUnitPrice(),
                transaction.getFees(), transaction.getOccurredAt(), transaction.getCreatedAt());
    }
}
