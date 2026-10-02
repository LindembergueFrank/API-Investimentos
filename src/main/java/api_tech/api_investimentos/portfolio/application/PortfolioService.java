package api_tech.api_investimentos.portfolio.application;

import api_tech.api_investimentos.portfolio.domain.Portfolio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;

    public PortfolioService(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    @Transactional
    public Portfolio create(CreatePortfolioCommand command) {
        return portfolioRepository.save(new Portfolio(UUID.randomUUID(), command.ownerId(), command.name().trim()));
    }

    @Transactional(readOnly = true)
    public Portfolio getById(UUID id, UUID ownerId) {
        return portfolioRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new PortfolioNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Portfolio> listByOwner(UUID ownerId) {
        return portfolioRepository.findAllByOwnerIdOrderByCreatedAtAsc(ownerId);
    }
}
