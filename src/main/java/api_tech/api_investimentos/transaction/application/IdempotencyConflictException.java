package api_tech.api_investimentos.transaction.application;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() { super("The requestId was already used with different transaction data."); }
}
