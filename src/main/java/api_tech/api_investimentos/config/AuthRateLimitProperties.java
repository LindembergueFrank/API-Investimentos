package api_tech.api_investimentos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "security.auth-rate-limit")
public record AuthRateLimitProperties(int maxRequests, Duration window) {

    public AuthRateLimitProperties {
        if (maxRequests < 1) {
            throw new IllegalArgumentException("security.auth-rate-limit.max-requests must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("security.auth-rate-limit.window must be positive");
        }
    }
}
