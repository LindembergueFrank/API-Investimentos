package api_tech.api_investimentos.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UpdateUserDto(
        @Schema(description = "Novo nome exibido do usuário", example = "investidor atualizado")
        @Size(min = 3, max = 50, message = "username must have between 3 and 50 characters")
        String username,

        @Schema(description = "Nova senha do usuário", example = "outra-senha-forte", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(min = 8, max = 72, message = "password must have between 8 and 72 characters")
        String password
) {
}
