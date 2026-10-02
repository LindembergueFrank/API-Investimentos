package api_tech.api_investimentos.portfolio.application;

import api_tech.api_investimentos.portfolio.domain.Portfolio;

import java.util.List;

public record PortfolioPage(List<Portfolio> items, int page, int size, long totalElements, int totalPages) {
}
