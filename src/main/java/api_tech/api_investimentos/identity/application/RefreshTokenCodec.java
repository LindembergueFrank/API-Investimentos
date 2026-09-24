package api_tech.api_investimentos.identity.application;

public interface RefreshTokenCodec {

    GeneratedRefreshToken generate();

    String hash(String token);
}
