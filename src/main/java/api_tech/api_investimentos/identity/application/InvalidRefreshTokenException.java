package api_tech.api_investimentos.identity.application;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("The refresh token is invalid, expired or revoked.");
    }
}
