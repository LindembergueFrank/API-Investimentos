package api_tech.api_investimentos.transaction.api;

import api_tech.api_investimentos.transaction.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull UUID requestId,
        @NotNull UUID portfolioId,
        @NotNull UUID assetId,
        @NotNull TransactionType type,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 8) BigDecimal quantity,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 8) BigDecimal unitPrice,
        @NotNull @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal fees,
        @NotNull @PastOrPresent Instant occurredAt
) {}
