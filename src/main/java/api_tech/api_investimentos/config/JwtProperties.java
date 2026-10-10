package api_tech.api_investimentos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Base64;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String issuer, Duration accessTokenTtl, String secretBase64) {

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("security.jwt.issuer is required");
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("security.jwt.access-token-ttl must be positive");
        }
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalArgumentException("security.jwt.secret-base64 is required");
        }

        byte[] secret = Base64.getDecoder().decode(secretBase64);
        if (secret.length < 32) {
            throw new IllegalArgumentException("security.jwt.secret-base64 must contain at least 256 bits");
        }
    }
}
