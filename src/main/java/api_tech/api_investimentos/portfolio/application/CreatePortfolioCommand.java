package api_tech.api_investimentos.portfolio.application;

import java.util.UUID;

public record CreatePortfolioCommand(UUID ownerId, String name) {
}
