package api_tech.api_investimentos.asset.infrastructure;

import api_tech.api_investimentos.asset.application.AssetAlreadyExistsException;
import api_tech.api_investimentos.asset.application.AssetPage;
import api_tech.api_investimentos.asset.application.AssetRepository;
import api_tech.api_investimentos.asset.domain.Asset;
import api_tech.api_investimentos.asset.domain.AssetMarket;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAssetRepository implements AssetRepository {
    private final SpringDataAssetRepository repository;

    public JpaAssetRepository(SpringDataAssetRepository repository) {
        this.repository = repository;
    }

    @Override
    public Asset save(Asset asset) {
        try {
            return repository.saveAndFlush(asset);
        } catch (DataIntegrityViolationException exception) {
            throw new AssetAlreadyExistsException(asset.getMarket(), asset.getTicker());
        }
    }

    @Override
    public boolean existsByMarketAndTicker(AssetMarket market, String ticker) {
        return repository.existsByMarketAndTicker(market, ticker);
    }

    @Override
    public AssetPage findPage(int page, int size) {
        var sort = Sort.by("market").ascending().and(Sort.by("ticker").ascending());
        var result = repository.findAll(PageRequest.of(page, size, sort));
        return new AssetPage(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
