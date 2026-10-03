package api_tech.api_investimentos.asset.application;

import api_tech.api_investimentos.asset.domain.AssetMarket;

public class AssetAlreadyExistsException extends RuntimeException {
    public AssetAlreadyExistsException(AssetMarket market, String ticker) {
        super("Asset already exists for market %s and ticker %s.".formatted(market, ticker));
    }
}
