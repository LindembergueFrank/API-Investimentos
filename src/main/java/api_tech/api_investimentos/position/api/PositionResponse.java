package api_tech.api_investimentos.position.api;

import api_tech.api_investimentos.position.domain.Position;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Posição atual derivada do livro de operações")
public record PositionResponse(
        @Schema(description = "Identificador do ativo", example = "0f53c5b2-f94f-4e36-a9cc-5ee2db815fb9")
        UUID assetId,

        @Schema(description = "Quantidade atualmente mantida", example = "12.00000000")
        BigDecimal quantity,

        @Schema(description = "Preço médio de aquisição, incluindo taxas de compra", example = "23.66666667")
        BigDecimal averagePrice,

        @Schema(description = "Custo contábil total da posição", example = "284.00000004")
        BigDecimal totalCost
) {
    static PositionResponse from(Position position) {
        return new PositionResponse(position.assetId(), position.quantity(), position.averagePrice(), position.totalCost());
    }
}
