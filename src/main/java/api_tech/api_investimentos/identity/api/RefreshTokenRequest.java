package api_tech.api_investimentos.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank
        @Size(max = 512)
        @Schema(description = "Token opaco recebido na autenticação")
        String refreshToken
) {
}
