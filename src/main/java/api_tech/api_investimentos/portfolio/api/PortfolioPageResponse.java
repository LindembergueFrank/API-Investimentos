package api_tech.api_investimentos.portfolio.api;

import api_tech.api_investimentos.portfolio.application.PortfolioPage;

import java.util.List;

public record PortfolioPageResponse(List<PortfolioResponse> items, int page, int size, long totalElements, int totalPages) {

    public static PortfolioPageResponse from(PortfolioPage portfolioPage) {
        return new PortfolioPageResponse(
                portfolioPage.items().stream().map(PortfolioResponse::from).toList(),
                portfolioPage.page(),
                portfolioPage.size(),
                portfolioPage.totalElements(),
                portfolioPage.totalPages()
        );
    }
}
