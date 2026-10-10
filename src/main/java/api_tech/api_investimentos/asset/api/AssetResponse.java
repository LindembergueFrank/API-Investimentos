package api_tech.api_investimentos.asset.api;

import api_tech.api_investimentos.asset.domain.Asset;
import api_tech.api_investimentos.asset.domain.AssetMarket;
import api_tech.api_investimentos.asset.domain.AssetType;
import java.time.Instant;
import java.util.UUID;

public record AssetResponse(UUID id, AssetMarket market, String ticker, AssetType type, String name, Instant createdAt) {
    public static AssetResponse from(Asset asset) {
        return new AssetResponse(asset.getId(), asset.getMarket(), asset.getTicker(), asset.getType(), asset.getName(), asset.getCreatedAt());
    }
}
