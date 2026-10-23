package api_tech.api_investimentos;

import api_tech.api_investimentos.common.infrastructure.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointIntegrationTest {

    private static final MediaType ACTUATOR_JSON = MediaType.parseMediaType(
            "application/vnd.spring-boot.actuator.v3+json"
    );

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExposeGeneralHealthWithoutAuthenticationOrInternalDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(ACTUATOR_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));
    }

    @Test
    void shouldExposeLivenessWithoutAuthenticationOrInternalDetails() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(ACTUATOR_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));
    }

    @Test
    void shouldExposeReadinessWithoutAuthenticationOrInternalDetails() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(ACTUATOR_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));
    }

    @Test
    void shouldNotExposeOtherActuatorEndpointsAnonymously() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotExposeHealthComponentsAnonymously() throws Exception {
        mockMvc.perform(get("/actuator/health/db"))
                .andExpect(status().isUnauthorized());
    }
}
