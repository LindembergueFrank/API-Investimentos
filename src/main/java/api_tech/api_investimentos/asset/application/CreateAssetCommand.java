package api_tech.api_investimentos.asset.application;

import api_tech.api_investimentos.asset.domain.AssetMarket;
import api_tech.api_investimentos.asset.domain.AssetType;

public record CreateAssetCommand(AssetMarket market, String ticker, AssetType type, String name) {
}
