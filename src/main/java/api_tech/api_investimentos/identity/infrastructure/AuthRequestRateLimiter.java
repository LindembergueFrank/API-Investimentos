package api_tech.api_investimentos.identity.infrastructure;

import api_tech.api_investimentos.config.AuthRateLimitProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class AuthRequestRateLimiter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final AuthRateLimitProperties properties;
    private final Clock clock;
    private final Map<String, RequestWindow> windows = new HashMap<>();
    private long nextCleanupEpochSecond = Long.MIN_VALUE;

    @Autowired
    public AuthRequestRateLimiter(AuthRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AuthRequestRateLimiter(AuthRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized Decision tryAcquire(String clientKey) {
        Instant now = clock.instant();
        var current = windows.get(clientKey);

        if (current == null && windows.size() >= MAX_TRACKED_CLIENTS) {
            removeExpiredWindows(now);
            if (windows.size() >= MAX_TRACKED_CLIENTS) {
                return Decision.rejected(properties.window().toSeconds());
            }
        }

        if (current == null || !now.isBefore(current.startedAt().plus(properties.window()))) {
            windows.put(clientKey, new RequestWindow(now, 1));
            return Decision.allowed();
        }
        if (current.requestCount() >= properties.maxRequests()) {
            return Decision.rejected(retryAfterSeconds(current, now));
        }
        windows.put(clientKey, new RequestWindow(current.startedAt(), current.requestCount() + 1));
        return Decision.allowed();
    }

    private void removeExpiredWindows(Instant now) {
        if (now.getEpochSecond() < nextCleanupEpochSecond) {
            return;
        }
        nextCleanupEpochSecond = now.getEpochSecond() + 1;
        windows.entrySet().removeIf(entry ->
                !now.isBefore(entry.getValue().startedAt().plus(properties.window())));
    }

    private long retryAfterSeconds(RequestWindow window, Instant now) {
        Duration remaining = Duration.between(now, window.startedAt().plus(properties.window()));
        long seconds = remaining.toSeconds();
        return remaining.minusSeconds(seconds).isZero() ? Math.max(1, seconds) : Math.max(1, seconds + 1);
    }

    private record RequestWindow(Instant startedAt, int requestCount) {
    }

    public record Decision(boolean permitted, long retryAfterSeconds) {

        private static Decision allowed() {
            return new Decision(true, 0);
        }

        private static Decision rejected(long retryAfterSeconds) {
            return new Decision(false, Math.max(1, retryAfterSeconds));
        }
    }
}
