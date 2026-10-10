package api_tech.api_investimentos.portfolio.application;

public class InvalidPaginationException extends RuntimeException {

    public InvalidPaginationException() {
        super("Page must be zero or greater and size must be between 1 and 100.");
    }
}
