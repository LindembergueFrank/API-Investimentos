package api_tech.api_investimentos.identity.api;

import api_tech.api_investimentos.identity.application.AuthenticatedSession;
import io.swagger.v3.oas.annotations.media.Schema;

public record AccessTokenResponse(
        @Schema(description = "JWT de acesso", format = "jwt") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Validade do access token em segundos", example = "900") long expiresIn,
        @Schema(description = "Token opaco de renovação") String refreshToken,
        @Schema(description = "Validade do refresh token em segundos", example = "2592000")
        long refreshExpiresIn
) {
    public static AccessTokenResponse from(AuthenticatedSession session) {
        return new AccessTokenResponse(
                session.accessToken().value(),
                "Bearer",
                session.accessToken().expiresInSeconds(),
                session.refreshToken(),
                session.refreshTokenExpiresInSeconds()
        );
    }
}
