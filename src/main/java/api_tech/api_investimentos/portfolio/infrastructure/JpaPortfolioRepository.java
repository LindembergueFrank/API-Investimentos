package api_tech.api_investimentos.portfolio.infrastructure;

import api_tech.api_investimentos.portfolio.application.PortfolioRepository;
import api_tech.api_investimentos.portfolio.domain.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaPortfolioRepository extends JpaRepository<Portfolio, UUID>, PortfolioRepository {
}
