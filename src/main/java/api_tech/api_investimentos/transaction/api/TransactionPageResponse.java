package api_tech.api_investimentos.transaction.api;

import api_tech.api_investimentos.transaction.application.TransactionPage;

import java.util.List;

public record TransactionPageResponse(
        List<TransactionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    static TransactionPageResponse from(TransactionPage source) {
        return new TransactionPageResponse(source.content().stream().map(TransactionResponse::from).toList(),
                source.page(), source.size(), source.totalElements(), source.totalPages());
    }
}
