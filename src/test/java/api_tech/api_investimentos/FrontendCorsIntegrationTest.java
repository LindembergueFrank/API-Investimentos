package api_tech.api_investimentos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "api.cors.allowed-origins=https://app.example.com")
@AutoConfigureMockMvc
class FrontendCorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAllowOnlyTheConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/v1/portfolios")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));

        mockMvc.perform(options("/v1/portfolios")
                        .header("Origin", "https://hostile.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void shouldPreserveCorsHeadersOnAuthenticationErrors() throws Exception {
        mockMvc.perform(get("/v1/portfolios").header("Origin", "https://app.example.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"));
    }
}
