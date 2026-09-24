package api_tech.api_investimentos.controller;

import jakarta.validation.constraints.Size;

public record UpdateUserDto(
        @Size(min = 3, max = 50, message = "username must have between 3 and 50 characters")
        String username,

        @Size(min = 8, max = 72, message = "password must have between 8 and 72 characters")
        String password
) {
}
