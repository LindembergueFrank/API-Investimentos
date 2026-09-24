package api_tech.api_investimentos.identity.application;

public record CreateUserCommand(String username, String email, String password) {
}
