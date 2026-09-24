package api_tech.api_investimentos;

import api_tech.api_investimentos.identity.api.LoginRequest;
import api_tech.api_investimentos.identity.api.RefreshTokenRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.auth-rate-limit.max-requests=2",
        "security.auth-rate-limit.window=PT1M"
})
@AutoConfigureMockMvc
class AuthRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldBlockAuthenticationAbuseWithoutLeakingCredentialsAndIsolateClients() throws Exception {
        String client = "203.0.113.10";
        String otherClient = "203.0.113.11";
        var request = new LoginRequest("missing@example.com", "secret-that-must-not-leak");

        invalidLogin(client, "192.0.2.1", request);
        invalidLogin(client, "192.0.2.2", request);

        mockMvc.perform(from(client, "/v1/auth/token")
                        .header("X-Forwarded-For", "192.0.2.3")
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Retry-After", matchesPattern("[1-9][0-9]*")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.type")
                        .value("urn:problem-type:api-investimentos:rate-limit-exceeded"))
                .andExpect(jsonPath("$.title").value("Rate limit exceeded"))
                .andExpect(content().string(not(containsString(request.email()))))
                .andExpect(content().string(not(containsString(request.password()))));

        invalidLogin(otherClient, "192.0.2.3", request);
    }

    @Test
    void shouldShareTheLimitAcrossAllAuthenticationEndpoints() throws Exception {
        String client = "198.51.100.20";
        var refreshRequest = new RefreshTokenRequest("unknown-refresh-token");

        mockMvc.perform(from(client, "/v1/auth/revoke")
                        .content(objectMapper.writeValueAsBytes(refreshRequest)))
                .andExpect(status().isNoContent());

        mockMvc.perform(from(client, "/v1/auth/refresh")
                        .content(objectMapper.writeValueAsBytes(refreshRequest)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(from(client, "/v1/auth/token")
                        .content(objectMapper.writeValueAsBytes(
                                new LoginRequest("missing@example.com", "another-secret"))))
                .andExpect(status().isTooManyRequests());
    }

    private void invalidLogin(String client, String forwardedFor, LoginRequest request) throws Exception {
        mockMvc.perform(from(client, "/v1/auth/token")
                        .header("X-Forwarded-For", forwardedFor)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder from(String client, String path) {
        return post(path)
                .with(request -> {
                    request.setRemoteAddr(client);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON);
    }
}
