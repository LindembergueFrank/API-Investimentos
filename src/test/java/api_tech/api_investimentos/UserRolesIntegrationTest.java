package api_tech.api_investimentos;

import api_tech.api_investimentos.identity.api.CreateUserDto;
import api_tech.api_investimentos.identity.api.LoginRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:userroles;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class UserRolesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void shouldAssignUserRoleToPublicRegistrationsAndDenyAdministrativeListing() throws Exception {
        var user = registerAndLogin("default-user", null);

        assertThat(jwtDecoder.decode(user.accessToken()).getClaimAsStringList("roles"))
                .containsExactly("USER");

        mockMvc.perform(get("/v1/users")
                        .header("Authorization", bearer(user.accessToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowOnlyAdministratorsToListUsers() throws Exception {
        var professional = registerAndLogin("professional", "PROFESSIONAL");
        var administrator = registerAndLogin("administrator", "ADMIN");

        assertThat(jwtDecoder.decode(professional.accessToken()).getClaimAsStringList("roles"))
                .containsExactly("PROFESSIONAL");
        mockMvc.perform(get("/v1/users")
                        .header("Authorization", bearer(professional.accessToken())))
                .andExpect(status().isForbidden());

        assertThat(jwtDecoder.decode(administrator.accessToken()).getClaimAsStringList("roles"))
                .containsExactly("ADMIN");
        mockMvc.perform(get("/v1/users")
                        .header("Authorization", bearer(administrator.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'professional')]").exists())
                .andExpect(jsonPath("$[?(@.username == 'administrator')]").exists());
    }

    private AuthenticatedUser registerAndLogin(String username, String role) throws Exception {
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

        if (role != null) {
            jdbcTemplate.update("UPDATE tb_user SET role = ? WHERE id = ?", role, userId);
        }

        var login = mockMvc.perform(post("/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode session = objectMapper.readTree(login.getResponse().getContentAsByteArray());
        return new AuthenticatedUser(session.get("accessToken").asText());
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record AuthenticatedUser(String accessToken) {
    }
}
