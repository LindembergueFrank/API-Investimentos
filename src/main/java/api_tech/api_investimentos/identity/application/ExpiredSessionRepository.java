package api_tech.api_investimentos.identity.application;

import java.time.Instant;

public interface ExpiredSessionRepository {

    int deleteExpiredFamilies(Instant cutoff, int batchSize);
}
