package api_tech.api_investimentos.portfolio.application;

import java.util.UUID;

public class PortfolioNotFoundException extends RuntimeException {

    public PortfolioNotFoundException(UUID id) {
        super("Portfolio not found: " + id);
    }
}
