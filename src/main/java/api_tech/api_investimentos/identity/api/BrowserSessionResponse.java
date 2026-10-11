package api_tech.api_investimentos.identity.api;

import api_tech.api_investimentos.identity.application.AuthenticatedSession;
import io.swagger.v3.oas.annotations.media.Schema;

public record BrowserSessionResponse(
        @Schema(description = "JWT de acesso", format = "jwt") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Validade do access token em segundos", example = "900") long expiresIn
) {
    public static BrowserSessionResponse from(AuthenticatedSession session) {
        return new BrowserSessionResponse(
                session.accessToken().value(),
                "Bearer",
                session.accessToken().expiresInSeconds()
        );
    }
}
