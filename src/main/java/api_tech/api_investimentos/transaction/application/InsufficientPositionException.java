package api_tech.api_investimentos.transaction.application;

public class InsufficientPositionException extends RuntimeException {
    public InsufficientPositionException() { super("The sale quantity exceeds the available position."); }
}
