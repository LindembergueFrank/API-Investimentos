package api_tech.api_investimentos.transaction.application;

import api_tech.api_investimentos.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateTransactionCommand(UUID requestId, UUID ownerId, UUID portfolioId, UUID assetId,
                                       TransactionType type, BigDecimal quantity, BigDecimal unitPrice,
                                       BigDecimal fees, Instant occurredAt) {}
