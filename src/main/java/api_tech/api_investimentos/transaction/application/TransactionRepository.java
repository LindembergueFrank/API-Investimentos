package api_tech.api_investimentos.transaction.application;

import api_tech.api_investimentos.transaction.domain.InvestmentTransaction;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository {
    InvestmentTransaction save(InvestmentTransaction transaction);
    Optional<InvestmentTransaction> findByPortfolioIdAndRequestId(UUID portfolioId, UUID requestId);
    BigDecimal netQuantity(UUID portfolioId, UUID assetId);
}
