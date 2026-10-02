package api_tech.api_investimentos;

import api_tech.api_investimentos.identity.api.CreateUserDto;
import api_tech.api_investimentos.identity.api.LoginRequest;
import api_tech.api_investimentos.portfolio.api.CreatePortfolioRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:portfolioflow;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class PortfolioFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateAndListOnlyAuthenticatedOwnersPortfolios() throws Exception {
        String ownerToken = registerAndLogin("portfolio-owner");
        String otherToken = registerAndLogin("portfolio-other");

        createPortfolio(ownerToken, "Long term");
        createPortfolio(ownerToken, "Short term");
        UUID otherPortfolio = createPortfolio(otherToken, "Other private portfolio");

        mockMvc.perform(get("/v1/portfolios")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));

        mockMvc.perform(get("/v1/portfolios?page=1&size=1")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/v1/portfolios/{id}", otherPortfolio)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void shouldRejectAnonymousAndInvalidPortfolioCreation() throws Exception {
        mockMvc.perform(post("/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new CreatePortfolioRequest("Private"))))
                .andExpect(status().isUnauthorized());

        String accessToken = registerAndLogin("invalid-portfolio");
        mockMvc.perform(post("/v1/portfolios")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new CreatePortfolioRequest("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("name"));

        mockMvc.perform(get("/v1/portfolios?page=-1&size=101")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request parameter"));
    }

    private UUID createPortfolio(String accessToken, String name) throws Exception {
        var result = mockMvc.perform(post("/v1/portfolios")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new CreatePortfolioRequest(name))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/v1/portfolios/[0-9a-f-]+")))
                .andReturn();

        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText());
    }

    private String registerAndLogin(String username) throws Exception {
        String email = username + "-" + UUID.randomUUID() + "@example.com";
        String password = "strong-password";
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new CreateUserDto(username, email, password))))
                .andExpect(status().isCreated());

        var login = mockMvc.perform(post("/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode session = objectMapper.readTree(login.getResponse().getContentAsByteArray());
        return session.get("accessToken").asText();
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
