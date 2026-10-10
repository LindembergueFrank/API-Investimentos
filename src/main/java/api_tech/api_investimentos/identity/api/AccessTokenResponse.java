package api_tech.api_investimentos.identity.api;

import api_tech.api_investimentos.identity.application.AccessToken;
import io.swagger.v3.oas.annotations.media.Schema;

public record AccessTokenResponse(
        @Schema(description = "JWT de acesso", format = "jwt") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Validade em segundos", example = "900") long expiresIn
) {
    public static AccessTokenResponse from(AccessToken token) {
        return new AccessTokenResponse(token.value(), "Bearer", token.expiresInSeconds());
    }
}
