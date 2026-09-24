package api_tech.api_investimentos.service;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    private final UUID userId;

    public UserNotFoundException(UUID userId) {
        super("User was not found.");
        this.userId = userId;
    }

    public UUID userId() {
        return userId;
    }
}
