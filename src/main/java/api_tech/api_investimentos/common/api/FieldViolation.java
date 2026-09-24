package api_tech.api_investimentos.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Violação de uma regra de validação da requisição")
public record FieldViolation(
        @Schema(example = "email") String field,
        @Schema(example = "email must be valid") String message
) {
}
