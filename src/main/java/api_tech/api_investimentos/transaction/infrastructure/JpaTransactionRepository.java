package api_tech.api_investimentos.transaction.infrastructure;

import api_tech.api_investimentos.transaction.application.TransactionRepository;
import api_tech.api_investimentos.transaction.domain.InvestmentTransaction;
import api_tech.api_investimentos.transaction.application.TransactionPage;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    @Override
    public TransactionPage findPageByPortfolioId(UUID portfolioId, int page, int size) {
        var sort = Sort.by("occurredAt").descending()
                .and(Sort.by("createdAt").descending())
                .and(Sort.by("id").descending());
        var result = repository.findAllByPortfolioId(portfolioId, PageRequest.of(page, size, sort));
        return new TransactionPage(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
    @Override public BigDecimal netQuantity(UUID portfolioId, UUID assetId) { return repository.netQuantity(portfolioId, assetId); }
}
