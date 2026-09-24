package api_tech.api_investimentos.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI investmentApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Investimentos")
                        .version("v1")
                        .description("API REST para evolução de um agregador de investimentos."));
    }
}
