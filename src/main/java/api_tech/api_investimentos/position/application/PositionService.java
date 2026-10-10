package api_tech.api_investimentos.position.application;

import api_tech.api_investimentos.portfolio.application.PortfolioNotFoundException;
import api_tech.api_investimentos.portfolio.application.PortfolioRepository;
import api_tech.api_investimentos.position.domain.Position;
import api_tech.api_investimentos.position.domain.PositionCalculator;
import api_tech.api_investimentos.transaction.application.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PositionService {
    private final PortfolioRepository portfolios;
    private final TransactionRepository transactions;

    public PositionService(PortfolioRepository portfolios, TransactionRepository transactions) {
        this.portfolios = portfolios;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public List<Position> listByPortfolio(UUID portfolioId, UUID ownerId) {
        portfolios.findByIdAndOwnerId(portfolioId, ownerId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
        return PositionCalculator.calculate(transactions.findAllByPortfolioIdInLedgerOrder(portfolioId));
    }
}
