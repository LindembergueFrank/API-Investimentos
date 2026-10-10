package api_tech.api_investimentos.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserDto(
        @Schema(description = "Nome exibido do usuário", example = "investidor")
        @NotBlank(message = "username is required")
        @Size(min = 3, max = 50, message = "username must have between 3 and 50 characters")
        String username,

        @Schema(description = "E-mail único do usuário", example = "investidor@example.com")
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,

        @Schema(description = "Senha do usuário", example = "uma-senha-forte", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must have between 8 and 72 characters")
        String password
) {
}
