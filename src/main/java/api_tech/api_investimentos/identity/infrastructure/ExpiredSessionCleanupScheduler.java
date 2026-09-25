package api_tech.api_investimentos.identity.infrastructure;

import api_tech.api_investimentos.identity.application.ExpiredSessionCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "security.refresh-token.cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ExpiredSessionCleanupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpiredSessionCleanupScheduler.class);

    private final ExpiredSessionCleanupService cleanupService;

    public ExpiredSessionCleanupScheduler(ExpiredSessionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(fixedDelayString = "${security.refresh-token.cleanup.interval:PT1H}")
    public void cleanupExpiredSessions() {
        int deletedRecords = cleanupService.cleanupBatch();
        if (deletedRecords > 0) {
            LOGGER.info("Removed {} expired refresh-token records", deletedRecords);
        }
    }
}
