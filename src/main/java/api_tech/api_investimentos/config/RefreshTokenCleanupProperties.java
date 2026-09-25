package api_tech.api_investimentos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "security.refresh-token.cleanup")
public record RefreshTokenCleanupProperties(Duration retention, int batchSize) {

    private static final int MAX_BATCH_SIZE = 10_000;

    public RefreshTokenCleanupProperties {
        if (retention == null || retention.isNegative()) {
            throw new IllegalArgumentException("security.refresh-token.cleanup.retention must not be negative");
        }
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "security.refresh-token.cleanup.batch-size must be between 1 and " + MAX_BATCH_SIZE
            );
        }
    }
}
