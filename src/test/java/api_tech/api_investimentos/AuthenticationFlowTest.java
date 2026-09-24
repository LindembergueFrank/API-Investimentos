package api_tech.api_investimentos;

import api_tech.api_investimentos.identity.api.CreateUserDto;
import api_tech.api_investimentos.identity.api.LoginRequest;
import api_tech.api_investimentos.identity.api.RefreshTokenRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldIssueAnAccessTokenAndProtectUserData() throws Exception {
        String email = "security-" + UUID.randomUUID() + "@example.com";
        String password = "strong-password";
        var registration = new CreateUserDto("secure-user", email, password);

        var registrationResult = mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(registration)))
                .andExpect(status().isCreated())
                .andReturn();
        String userId = objectMapper.readTree(registrationResult.getResponse().getContentAsByteArray())
                .get("id")
                .asText();

        mockMvc.perform(get("/v1/users/{id}", userId))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Authentication required"));

        var loginResult = mockMvc.perform(post("/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andReturn();
        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsByteArray())
                .get("accessToken")
                .asText();

        mockMvc.perform(get("/v1/users/{id}", userId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(post("/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new LoginRequest(email, "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid credentials"));
    }

    @Test
    void shouldRotateRefreshTokensAndRevokeTheFamilyWhenAnOldTokenIsReused() throws Exception {
        String email = "rotation-" + UUID.randomUUID() + "@example.com";
        String password = "strong-password";
        register(email, password);

        var loginResult = login(email, password);
        String firstRefreshToken = loginResult.get("refreshToken").asText();

        var refreshResult = mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new RefreshTokenRequest(firstRefreshToken))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.refreshExpiresIn").value(2592000))
                .andReturn();
        String secondRefreshToken = objectMapper.readTree(refreshResult.getResponse().getContentAsByteArray())
                .get("refreshToken")
                .asText();

        assertNotEquals(firstRefreshToken, secondRefreshToken);
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_refresh_token WHERE token_hash IN (?, ?)",
                Integer.class,
                firstRefreshToken,
                secondRefreshToken
        ));

        refreshExpectingUnauthorized(firstRefreshToken);
        refreshExpectingUnauthorized(secondRefreshToken);
    }

    @Test
    void shouldRevokeARefreshTokenWithoutDisclosingWhetherItExists() throws Exception {
        String email = "revocation-" + UUID.randomUUID() + "@example.com";
        String password = "strong-password";
        register(email, password);
        String refreshToken = login(email, password).get("refreshToken").asText();

        revoke("unknown-refresh-token");
        revoke(refreshToken);
        refreshExpectingUnauthorized(refreshToken);
    }

    @Test
    void shouldRejectOversizedRefreshTokensWithoutEchoingThem() throws Exception {
        String rejectedToken = "s".repeat(513);

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new RefreshTokenRequest(rejectedToken))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Request validation failed"))
                .andExpect(content().string(not(containsString(rejectedToken))));
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new CreateUserDto("secure-user", email, password))))
                .andExpect(status().isCreated());
    }

    private com.fasterxml.jackson.databind.JsonNode login(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void revoke(String refreshToken) throws Exception {
        mockMvc.perform(post("/v1/auth/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    private void refreshExpectingUnauthorized(String refreshToken) throws Exception {
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:problem-type:api-investimentos:invalid-refresh-token"))
                .andExpect(jsonPath("$.title").value("Invalid refresh token"));
    }
}
