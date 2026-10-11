package api_tech.api_investimentos.asset.infrastructure;

import api_tech.api_investimentos.asset.domain.Asset;
import api_tech.api_investimentos.asset.domain.AssetMarket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface SpringDataAssetRepository extends JpaRepository<Asset, UUID> {
    boolean existsByMarketAndTicker(AssetMarket market, String ticker);
    @Query("""
            select asset from Asset asset
            where locate(lower(:query), lower(asset.ticker)) > 0
               or locate(lower(:query), lower(asset.name)) > 0
            """)
    Page<Asset> search(@Param("query") String query, Pageable pageable);
}
