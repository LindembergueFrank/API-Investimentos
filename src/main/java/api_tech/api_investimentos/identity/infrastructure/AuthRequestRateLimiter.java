package api_tech.api_investimentos.identity.infrastructure;

import api_tech.api_investimentos.config.AuthRateLimitProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AuthRequestRateLimiter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final AuthRateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, RequestWindow> windows = new ConcurrentHashMap<>();
    private final AtomicLong nextCleanupEpochSecond = new AtomicLong();

    public AuthRequestRateLimiter(AuthRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AuthRequestRateLimiter(AuthRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Decision tryAcquire(String clientKey) {
        Instant now = clock.instant();

        if (!windows.containsKey(clientKey) && windows.size() >= MAX_TRACKED_CLIENTS) {
            removeExpiredWindows(now);
            if (!windows.containsKey(clientKey) && windows.size() >= MAX_TRACKED_CLIENTS) {
                return Decision.rejected(properties.window().toSeconds());
            }
        }

        var decision = new AtomicReference<Decision>();
        windows.compute(clientKey, (key, current) -> {
            if (current == null || !now.isBefore(current.startedAt().plus(properties.window()))) {
                decision.set(Decision.allowed());
                return new RequestWindow(now, 1);
            }

            if (current.requestCount() >= properties.maxRequests()) {
                decision.set(Decision.rejected(retryAfterSeconds(current, now)));
                return current;
            }

            decision.set(Decision.allowed());
            return new RequestWindow(current.startedAt(), current.requestCount() + 1);
        });
        return decision.get();
    }

    private void removeExpiredWindows(Instant now) {
        long nextCleanup = nextCleanupEpochSecond.get();
        if (now.getEpochSecond() < nextCleanup
                || !nextCleanupEpochSecond.compareAndSet(nextCleanup, now.getEpochSecond() + 1)) {
            return;
        }
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
