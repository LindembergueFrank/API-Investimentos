package api_tech.api_investimentos.transaction.application;

import api_tech.api_investimentos.asset.application.AssetRepository;
import api_tech.api_investimentos.portfolio.application.PortfolioNotFoundException;
import api_tech.api_investimentos.portfolio.application.PortfolioRepository;
import api_tech.api_investimentos.portfolio.application.InvalidPaginationException;
import api_tech.api_investimentos.transaction.domain.InvestmentTransaction;
import api_tech.api_investimentos.transaction.domain.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransactionService {
    private final PortfolioRepository portfolios;
    private final AssetRepository assets;
    private final TransactionRepository transactions;

    public TransactionService(PortfolioRepository portfolios, AssetRepository assets, TransactionRepository transactions) {
        this.portfolios = portfolios;
        this.assets = assets;
        this.transactions = transactions;
    }

    @Transactional
    public CreateTransactionResult create(CreateTransactionCommand command) {
        portfolios.lockByIdAndOwnerId(command.portfolioId(), command.ownerId())
                .orElseThrow(() -> new PortfolioNotFoundException(command.portfolioId()));
        if (assets.findById(command.assetId()).isEmpty()) throw new AssetNotFoundException();

        var existing = transactions.findByPortfolioIdAndRequestId(command.portfolioId(), command.requestId());
        if (existing.isPresent()) {
            if (!existing.get().hasSameContent(command.type(), command.assetId(), command.quantity(),
                    command.unitPrice(), command.fees(), command.occurredAt())) throw new IdempotencyConflictException();
            return new CreateTransactionResult(existing.get(), false);
        }

        if (command.type() == TransactionType.SELL
                && transactions.netQuantity(command.portfolioId(), command.assetId()).compareTo(command.quantity()) < 0) {
            throw new InsufficientPositionException();
        }
        var transaction = new InvestmentTransaction(UUID.randomUUID(), command.requestId(), command.portfolioId(),
                command.assetId(), command.type(), command.quantity(), command.unitPrice(), command.fees(), command.occurredAt());
        return new CreateTransactionResult(transactions.save(transaction), true);
    }

    @Transactional(readOnly = true)
    public TransactionPage listByPortfolio(UUID portfolioId, UUID ownerId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidPaginationException();
        }
        portfolios.findByIdAndOwnerId(portfolioId, ownerId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
        return transactions.findPageByPortfolioId(portfolioId, page, size);
    }
}
