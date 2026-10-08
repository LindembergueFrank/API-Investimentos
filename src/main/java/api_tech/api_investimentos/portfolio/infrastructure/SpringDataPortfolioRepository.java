package api_tech.api_investimentos.portfolio.infrastructure;

import api_tech.api_investimentos.portfolio.domain.Portfolio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.UUID;
import java.util.Optional;

interface SpringDataPortfolioRepository extends JpaRepository<Portfolio, UUID> {
    Optional<Portfolio> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Portfolio> findForUpdateByIdAndOwnerId(UUID id, UUID ownerId);

    Page<Portfolio> findAllByOwnerId(UUID ownerId, Pageable pageable);
}
