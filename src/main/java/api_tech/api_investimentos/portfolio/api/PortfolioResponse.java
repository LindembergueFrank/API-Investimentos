package api_tech.api_investimentos.portfolio.api;

import api_tech.api_investimentos.portfolio.domain.Portfolio;

import java.time.Instant;
import java.util.UUID;

public record PortfolioResponse(UUID id, String name, Instant createdAt, Instant updatedAt) {

    public static PortfolioResponse from(Portfolio portfolio) {
        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt()
        );
    }
}
