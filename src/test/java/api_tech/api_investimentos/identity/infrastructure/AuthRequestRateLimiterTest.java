package api_tech.api_investimentos.identity.infrastructure;

import api_tech.api_investimentos.config.AuthRateLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthRequestRateLimiterTest {

    @Test
    void shouldEnforceGlobalCapacityAndReclaimExpiredWindows() throws Exception {
        var clock = mock(Clock.class);
        var now = Instant.parse("2026-01-01T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        var limiter = new AuthRequestRateLimiter(new AuthRateLimitProperties(2, Duration.ofMinutes(1)), clock);
        for (int i = 0; i < 9_999; i++) {
            assertTrue(limiter.tryAcquire("existing-" + i).permitted());
        }

        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var decisions = new ArrayList<Future<AuthRequestRateLimiter.Decision>>();
            for (int i = 0; i < 8; i++) {
                String client = "new-" + i;
                decisions.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    return limiter.tryAcquire(client);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int admitted = 0;
            for (var decision : decisions) {
                if (decision.get(5, TimeUnit.SECONDS).permitted()) admitted++;
            }
            assertEquals(1, admitted);
            assertFalse(limiter.tryAcquire("overflow").permitted());
            assertTrue(limiter.tryAcquire("existing-0").permitted());
            assertFalse(limiter.tryAcquire("existing-0").permitted());

            when(clock.instant()).thenReturn(now.plusSeconds(60));
            assertTrue(limiter.tryAcquire("overflow").permitted());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldEnforceTheLimitAtomicallyUnderConcurrency() throws Exception {
        var rateLimiter = new AuthRequestRateLimiter(
                new AuthRateLimitProperties(10, Duration.ofMinutes(1))
        );
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(16);

        try {
            var attempts = new ArrayList<Future<AuthRequestRateLimiter.Decision>>();
            for (int index = 0; index < 100; index++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return rateLimiter.tryAcquire("same-client");
                }));
            }

            start.countDown();
            long permitted = 0;
            for (var attempt : attempts) {
                if (attempt.get().permitted()) {
                    permitted++;
                }
            }

            assertEquals(10, permitted);
        } finally {
            executor.shutdownNow();
        }
    }
}
