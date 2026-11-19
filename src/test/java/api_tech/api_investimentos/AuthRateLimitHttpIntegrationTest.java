package api_tech.api_investimentos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-http-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "security.auth-rate-limit.max-requests=2",
        "security.auth-rate-limit.window=PT1H"
})
class AuthRateLimitHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldEnforceSharedQuotaAcrossConcurrentRealHttpRequests() throws Exception {
        int requestCount = 8;
        var ready = new CountDownLatch(requestCount);
        var start = new CountDownLatch(1);
        try (var client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
             var executor = Executors.newFixedThreadPool(requestCount)) {
            var responses = new ArrayList<Future<HttpResponse<String>>>();
            for (int i = 0; i < requestCount; i++) {
                String path = i % 2 == 0 ? "/v1/auth/revoke" : "/v1/auth/browser/revoke";
                var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"refreshToken\":\"synthetic-unknown-refresh-token\"}"))
                        .build();
                responses.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent start timed out");
                    }
                    return client.send(request, HttpResponse.BodyHandlers.ofString());
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int admitted = 0;
            int rejected = 0;
            for (var responseFuture : responses) {
                var response = responseFuture.get(15, TimeUnit.SECONDS);
                if (response.statusCode() == 204) {
                    admitted++;
                } else {
                    assertEquals(429, response.statusCode());
                    rejected++;
                    assertTrue(response.headers().firstValue("Content-Type").orElse("")
                            .startsWith("application/problem+json"));
                    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
                    assertTrue(response.headers().firstValue("Retry-After").orElse("")
                            .matches("[1-9][0-9]*"));
                    assertTrue(response.headers().allValues("Set-Cookie").isEmpty());
                    var problem = objectMapper.readTree(response.body());
                    assertEquals(429, problem.path("status").asInt());
                    assertEquals("urn:problem-type:api-investimentos:rate-limit-exceeded",
                            problem.path("type").asText());
                    assertEquals(response.uri().getPath(), problem.path("instance").asText());
                    assertFalse(response.body().contains("synthetic-unknown-refresh-token"));
                }
            }
            assertEquals(2, admitted);
            assertEquals(6, rejected);
        }
    }
}
