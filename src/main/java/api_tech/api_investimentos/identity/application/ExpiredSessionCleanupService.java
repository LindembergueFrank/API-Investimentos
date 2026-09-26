package api_tech.api_investimentos.identity.application;

import api_tech.api_investimentos.config.RefreshTokenCleanupProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class ExpiredSessionCleanupService {

    private final ExpiredSessionRepository expiredSessionRepository;
    private final RefreshTokenCleanupProperties properties;
    private final Clock clock;

    public ExpiredSessionCleanupService(
            ExpiredSessionRepository expiredSessionRepository,
            RefreshTokenCleanupProperties properties,
            Clock clock
    ) {
        this.expiredSessionRepository = expiredSessionRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public int cleanupBatch() {
        var cutoff = clock.instant().minus(properties.retention());
        return expiredSessionRepository.deleteExpiredFamilies(cutoff, properties.batchSize());
    }
}
