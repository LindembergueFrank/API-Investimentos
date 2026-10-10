package api_tech.api_investimentos.identity.infrastructure;

import api_tech.api_investimentos.config.AuthRateLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthRequestRateLimiterTest {

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
