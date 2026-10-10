package api_tech.api_investimentos.transaction.infrastructure;

import api_tech.api_investimentos.transaction.domain.InvestmentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataTransactionRepository extends JpaRepository<InvestmentTransaction, UUID> {
    Optional<InvestmentTransaction> findByPortfolioIdAndRequestId(UUID portfolioId, UUID requestId);
    Page<InvestmentTransaction> findAllByPortfolioId(UUID portfolioId, Pageable pageable);
    List<InvestmentTransaction> findAllByPortfolioId(UUID portfolioId, Sort sort);

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN type = 'BUY' THEN quantity ELSE -quantity END), 0)
            FROM investment_transaction WHERE portfolio_id = :portfolioId AND asset_id = :assetId
            """, nativeQuery = true)
    BigDecimal netQuantity(@Param("portfolioId") UUID portfolioId, @Param("assetId") UUID assetId);
}
