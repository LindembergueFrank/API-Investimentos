package api_tech.api_investimentos.portfolio.infrastructure;

import api_tech.api_investimentos.portfolio.application.PortfolioPage;
import api_tech.api_investimentos.portfolio.application.PortfolioRepository;
import api_tech.api_investimentos.portfolio.domain.Portfolio;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaPortfolioRepository implements PortfolioRepository {

    private final SpringDataPortfolioRepository repository;

    public JpaPortfolioRepository(SpringDataPortfolioRepository repository) {
        this.repository = repository;
    }

    @Override
    public <S extends Portfolio> S save(S portfolio) {
        return repository.save(portfolio);
    }

    @Override
    public Optional<Portfolio> findByIdAndOwnerId(UUID id, UUID ownerId) {
        return repository.findByIdAndOwnerId(id, ownerId);
    }

    @Override
    public PortfolioPage findPageByOwnerId(UUID ownerId, int page, int size) {
        var sort = Sort.by("createdAt").ascending().and(Sort.by("id").ascending());
        var result = repository.findAllByOwnerId(ownerId, PageRequest.of(page, size, sort));
        return new PortfolioPage(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
