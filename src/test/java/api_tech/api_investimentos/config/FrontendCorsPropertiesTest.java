package api_tech.api_investimentos.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FrontendCorsPropertiesTest {

    @Test
    void shouldNormalizeExactOrigins() {
        var properties = new FrontendCorsProperties(List.of(
                " https://app.example.com ",
                "https://app.example.com",
                "http://localhost:5173",
                " "
        ));

        assertThat(properties.allowedOrigins())
                .containsExactly("https://app.example.com", "http://localhost:5173");
    }

    @Test
    void shouldRejectWildcardsAndUrlComponentsBeyondTheOrigin() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FrontendCorsProperties(List.of("https://*.example.com")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FrontendCorsProperties(List.of("https://app.example.com/path")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FrontendCorsProperties(List.of("https://app.example.com?source=test")));
    }
}
