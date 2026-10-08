package api_tech.api_investimentos.transaction.infrastructure;

import api_tech.api_investimentos.transaction.application.TransactionRepository;
import api_tech.api_investimentos.transaction.domain.InvestmentTransaction;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaTransactionRepository implements TransactionRepository {
    private final SpringDataTransactionRepository repository;

    public JpaTransactionRepository(SpringDataTransactionRepository repository) { this.repository = repository; }

    @Override public InvestmentTransaction save(InvestmentTransaction transaction) { return repository.saveAndFlush(transaction); }
    @Override public Optional<InvestmentTransaction> findByPortfolioIdAndRequestId(UUID portfolioId, UUID requestId) {
        return repository.findByPortfolioIdAndRequestId(portfolioId, requestId);
    }
    @Override public BigDecimal netQuantity(UUID portfolioId, UUID assetId) { return repository.netQuantity(portfolioId, assetId); }
}
