package api_tech.api_investimentos.identity.application;

public record AuthenticatedSession(
        AccessToken accessToken,
        String refreshToken,
        long refreshTokenExpiresInSeconds
) {
}
