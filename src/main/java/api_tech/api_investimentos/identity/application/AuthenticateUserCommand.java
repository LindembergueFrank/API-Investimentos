package api_tech.api_investimentos.identity.application;

public record AuthenticateUserCommand(String email, String password) {
}
