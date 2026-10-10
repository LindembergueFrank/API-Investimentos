package api_tech.api_investimentos.transaction.application;

import api_tech.api_investimentos.transaction.domain.InvestmentTransaction;

import java.util.List;

public record TransactionPage(
        List<InvestmentTransaction> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
