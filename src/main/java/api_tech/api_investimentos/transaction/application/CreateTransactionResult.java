package api_tech.api_investimentos.transaction.application;

import api_tech.api_investimentos.transaction.domain.InvestmentTransaction;

public record CreateTransactionResult(InvestmentTransaction transaction, boolean created) {}
