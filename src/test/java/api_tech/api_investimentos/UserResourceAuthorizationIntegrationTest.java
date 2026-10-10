package api_tech.api_investimentos;

import api_tech.api_investimentos.identity.api.CreateUserDto;
import api_tech.api_investimentos.identity.api.LoginRequest;
import api_tech.api_investimentos.identity.api.UpdateUserDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:userauthorization;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class UserResourceAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldAllowOnlyTheOwnerToReadAndUpdateAUserResource() throws Exception {
        var owner = registerAndLogin("owner");
        var other = registerAndLogin("other");

        mockMvc.perform(get("/v1/users/{id}", owner.userId())
                        .header("Authorization", bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.userId().toString()));

        mockMvc.perform(patch("/v1/users/{id}", owner.userId())
                        .header("Authorization", bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new UpdateUserDto("updated-owner", null))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/users/{id}", other.userId())
                        .header("Authorization", bearer(owner.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Access denied"));

        mockMvc.perform(patch("/v1/users/{id}", other.userId())
                        .header("Authorization", bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new UpdateUserDto("stolen-account", null))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/v1/users/{id}", other.userId())
                        .header("Authorization", bearer(other.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("other"));
    }

    @Test
    void shouldDenyCrossUserDeletionAndUserListing() throws Exception {
        var owner = registerAndLogin("delete-owner");
        var other = registerAndLogin("delete-other");

        mockMvc.perform(delete("/v1/users/{id}", other.userId())
                        .header("Authorization", bearer(owner.accessToken())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/v1/users")
                        .header("Authorization", bearer(owner.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Access denied"));

        mockMvc.perform(get("/v1/users/{id}", other.userId())
                        .header("Authorization", bearer(other.accessToken())))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/v1/users/{id}", owner.userId())
                        .header("Authorization", bearer(owner.accessToken())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/users/{id}", owner.userId())
                        .header("Authorization", bearer(owner.accessToken())))
                .andExpect(status().isNotFound());
    }

    private AuthenticatedUser registerAndLogin(String username) throws Exception {
        String email = username + "-" + UUID.randomUUID() + "@example.com";
        String password = "strong-password";
        var registration = mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new CreateUserDto(username, email, password))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID userId = UUID.fromString(objectMapper.readTree(registration.getResponse().getContentAsByteArray())
                .get("id")
                .asText());

        var login = mockMvc.perform(post("/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode session = objectMapper.readTree(login.getResponse().getContentAsByteArray());

        return new AuthenticatedUser(userId, session.get("accessToken").asText());
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record AuthenticatedUser(UUID userId, String accessToken) {
    }
}
