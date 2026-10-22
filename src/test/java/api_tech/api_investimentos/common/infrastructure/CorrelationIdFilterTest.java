package api_tech.api_investimentos.common.infrastructure;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void shouldGenerateServerControlledCorrelationIdAndRemoveItAfterRequest() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "client-controlled-value");
        var response = new MockHttpServletResponse();
        var correlationIdSeenByApplication = new AtomicReference<String>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                correlationIdSeenByApplication.set(MDC.get(CorrelationIdFilter.MDC_KEY))
        );

        String responseCorrelationId = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(responseCorrelationId)
                .isNotEqualTo("client-controlled-value")
                .isEqualTo(correlationIdSeenByApplication.get());
        assertThatCodeIsUuid(responseCorrelationId);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void shouldGenerateDistinctCorrelationIdForEachRequest() throws Exception {
        var firstResponse = new MockHttpServletResponse();
        var secondResponse = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), firstResponse, (request, response) -> { });
        filter.doFilter(new MockHttpServletRequest(), secondResponse, (request, response) -> { });

        assertThat(firstResponse.getHeader(CorrelationIdFilter.HEADER_NAME))
                .isNotEqualTo(secondResponse.getHeader(CorrelationIdFilter.HEADER_NAME));
    }

    @Test
    void shouldRemoveCorrelationIdWhenRequestProcessingFails() {
        assertThatThrownBy(() -> filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, response) -> {
                    throw new IllegalStateException("simulated failure");
                }
        )).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    private static void assertThatCodeIsUuid(String value) {
        assertThat(value).isNotNull();
        assertThat(UUID.fromString(value).toString()).isEqualTo(value);
    }
}
