package api_tech.api_investimentos;

import api_tech.api_investimentos.asset.api.CreateAssetRequest;
import api_tech.api_investimentos.asset.domain.AssetMarket;
import api_tech.api_investimentos.asset.domain.AssetType;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:assetcatalog;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AssetCatalogIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRestrictCreationToAdminAndExposeNormalizedCatalogToAuthenticatedUsers() throws Exception {
        var user = registerAndLogin("asset-user", false);
        var admin = registerAndLogin("asset-admin", true);
        var request = new CreateAssetRequest(AssetMarket.B3, "petr4", AssetType.STOCK, "Petróleo Brasileiro");

        mockMvc.perform(post("/v1/assets").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/v1/assets").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticker").value("PETR4"))
                .andExpect(jsonPath("$.market").value("B3"));

        mockMvc.perform(get("/v1/assets").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].ticker").value("PETR4"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldRejectDuplicateMarketAndTickerRegardlessOfCase() throws Exception {
        var admin = registerAndLogin("duplicate-admin", true);
        create(admin, new CreateAssetRequest(AssetMarket.B3, "HGLG11", AssetType.FII, "CSHG Logística"));

        mockMvc.perform(post("/v1/assets").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new CreateAssetRequest(AssetMarket.B3, "hglg11", AssetType.FII, "Duplicado"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource conflict"));
    }

    private void create(String token, CreateAssetRequest request) throws Exception {
        mockMvc.perform(post("/v1/assets").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated());
    }

    private String registerAndLogin(String username, boolean admin) throws Exception {
        String email = username + "-" + UUID.randomUUID() + "@example.com";
        String password = "strong-password";
        var registration = mockMvc.perform(post("/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new CreateUserDto(username, email, password))))
                .andExpect(status().isCreated()).andReturn();
        if (admin) {
            UUID id = UUID.fromString(objectMapper.readTree(registration.getResponse().getContentAsByteArray()).get("id").asText());
            jdbcTemplate.update("UPDATE tb_user SET role = 'ADMIN' WHERE id = ?", id);
        }
        var login = mockMvc.perform(post("/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new LoginRequest(email, password))))
                .andExpect(status().isOk()).andReturn();
        JsonNode session = objectMapper.readTree(login.getResponse().getContentAsByteArray());
        return session.get("accessToken").asText();
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
