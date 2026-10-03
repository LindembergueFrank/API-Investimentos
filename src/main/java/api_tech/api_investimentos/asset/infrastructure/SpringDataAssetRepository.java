package api_tech.api_investimentos.asset.infrastructure;

import api_tech.api_investimentos.asset.domain.Asset;
import api_tech.api_investimentos.asset.domain.AssetMarket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataAssetRepository extends JpaRepository<Asset, UUID> {
    boolean existsByMarketAndTicker(AssetMarket market, String ticker);
}
