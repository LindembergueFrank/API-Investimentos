package api_tech.api_investimentos.asset.application;

import api_tech.api_investimentos.asset.domain.Asset;
import api_tech.api_investimentos.asset.domain.AssetMarket;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository {
    Asset save(Asset asset);
    boolean existsByMarketAndTicker(AssetMarket market, String ticker);
    AssetPage findPage(int page, int size);
    Optional<Asset> findById(UUID id);
}
