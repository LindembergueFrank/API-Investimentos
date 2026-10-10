package api_tech.api_investimentos.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "investidor@example.com")
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,

        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "password is required")
        String password
) {
}
