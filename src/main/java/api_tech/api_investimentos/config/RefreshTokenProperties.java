package api_tech.api_investimentos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "security.refresh-token")
public record RefreshTokenProperties(Duration ttl) {

    public RefreshTokenProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("security.refresh-token.ttl must be positive");
        }
    }
}
