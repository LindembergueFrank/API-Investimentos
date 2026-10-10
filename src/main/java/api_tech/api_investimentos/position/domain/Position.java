package api_tech.api_investimentos.position.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record Position(UUID assetId, BigDecimal quantity, BigDecimal averagePrice, BigDecimal totalCost) {
}
