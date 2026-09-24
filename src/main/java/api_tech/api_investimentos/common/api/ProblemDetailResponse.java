package api_tech.api_investimentos.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.util.List;

@Schema(name = "ProblemDetail", description = "Erro HTTP no formato RFC 9457")
public record ProblemDetailResponse(
        @Schema(example = "about:blank")
        URI type,

        @Schema(example = "Bad Request")
        String title,

        @Schema(example = "400")
        int status,

        @Schema(example = "Request validation failed")
        String detail,

        @Schema(example = "/v1/users")
        URI instance,

        @Schema(description = "Violações por campo, presente apenas em erros de validação")
        List<FieldViolation> errors
) {
}
