package api_tech.api_investimentos.portfolio.application;

import api_tech.api_investimentos.portfolio.domain.Portfolio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioRepository {

    <S extends Portfolio> S save(S portfolio);

    Optional<Portfolio> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Portfolio> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);
}
