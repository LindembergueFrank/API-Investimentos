package api_tech.api_investimentos.position.api;

import api_tech.api_investimentos.position.domain.Position;

import java.math.BigDecimal;
import java.util.UUID;

public record PositionResponse(UUID assetId, BigDecimal quantity, BigDecimal averagePrice, BigDecimal totalCost) {
    static PositionResponse from(Position position) {
        return new PositionResponse(position.assetId(), position.quantity(), position.averagePrice(), position.totalCost());
    }
}
