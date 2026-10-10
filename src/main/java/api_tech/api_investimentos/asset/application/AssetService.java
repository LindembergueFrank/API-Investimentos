package api_tech.api_investimentos.asset.application;

import api_tech.api_investimentos.asset.domain.Asset;
import api_tech.api_investimentos.portfolio.application.InvalidPaginationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AssetService {
    private final AssetRepository repository;

    public AssetService(AssetRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Asset create(CreateAssetCommand command) {
        String ticker = command.ticker().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByMarketAndTicker(command.market(), ticker)) {
            throw new AssetAlreadyExistsException(command.market(), ticker);
        }
        return repository.save(new Asset(UUID.randomUUID(), command.market(), ticker, command.type(), command.name().trim()));
    }

    @Transactional(readOnly = true)
    public AssetPage list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidPaginationException();
        }
        return repository.findPage(page, size);
    }
}
