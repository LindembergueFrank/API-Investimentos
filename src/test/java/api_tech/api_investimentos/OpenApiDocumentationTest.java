package api_tech.api_investimentos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldPublishTheUserHttpContractWithoutAResponsePassword() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("API de Investimentos"))
                .andExpect(jsonPath("$.paths['/v1/users'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/v1/users/{id}'].patch.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/v1/auth/token'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/v1/auth/refresh'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/v1/auth/revoke'].post.responses['204']").exists())
                .andExpect(jsonPath("$.components.schemas.CreateUserDto.properties.password.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.UserResponseDto.properties.password").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail").exists());
    }
}
