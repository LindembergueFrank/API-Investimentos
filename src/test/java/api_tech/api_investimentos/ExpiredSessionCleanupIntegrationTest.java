package api_tech.api_investimentos;

import api_tech.api_investimentos.identity.application.ExpiredSessionCleanupService;
import api_tech.api_investimentos.identity.application.RefreshTokenRepository;
import api_tech.api_investimentos.identity.application.UserRepository;
import api_tech.api_investimentos.identity.domain.RefreshToken;
import api_tech.api_investimentos.identity.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
@Import(ExpiredSessionCleanupIntegrationTest.FixedClockConfig.class)
@TestPropertySource(properties = {
        "security.refresh-token.cleanup.enabled=false",
        "security.refresh-token.cleanup.retention=P7D",
        "security.refresh-token.cleanup.batch-size=2"
})
class ExpiredSessionCleanupIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-25T20:00:00Z");

    @Autowired
    private ExpiredSessionCleanupService cleanupService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldDeleteOnlyFamiliesWhoseEveryTokenExceededTheRetentionPeriod() {
        UUID userId = saveUser();
        UUID eligibleFamily = UUID.randomUUID();
        saveToken(userId, eligibleFamily, NOW.minusSeconds(15 * 24 * 60 * 60L));
        saveToken(userId, eligibleFamily, NOW.minusSeconds(14 * 24 * 60 * 60L));

        UUID recentlyExpiredFamily = UUID.randomUUID();
        saveToken(userId, recentlyExpiredFamily, NOW.minusSeconds(2 * 24 * 60 * 60L));

        UUID familyWithActiveDescendant = UUID.randomUUID();
        saveToken(userId, familyWithActiveDescendant, NOW.minusSeconds(30 * 24 * 60 * 60L));
        saveToken(userId, familyWithActiveDescendant, NOW.plusSeconds(24 * 60 * 60L));

        assertEquals(2, cleanupService.cleanupBatch());
        assertEquals(3, tokenCount());
        assertEquals(0, cleanupService.cleanupBatch());
        assertEquals(3, tokenCount());
    }

    @Test
    void shouldLimitWorkByFamilyAndRemainIdempotent() {
        UUID userId = saveUser();
        saveToken(userId, UUID.randomUUID(), NOW.minusSeconds(30 * 24 * 60 * 60L));
        saveToken(userId, UUID.randomUUID(), NOW.minusSeconds(20 * 24 * 60 * 60L));
        saveToken(userId, UUID.randomUUID(), NOW.minusSeconds(10 * 24 * 60 * 60L));

        assertEquals(2, cleanupService.cleanupBatch());
        assertEquals(1, tokenCount());
        assertEquals(1, cleanupService.cleanupBatch());
        assertEquals(0, tokenCount());
        assertEquals(0, cleanupService.cleanupBatch());
    }

    private UUID saveUser() {
        UUID id = UUID.randomUUID();
        userRepository.save(new User(
                id,
                "cleanup-user",
                "cleanup-" + id + "@example.com",
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                NOW,
                NOW
        ));
        return id;
    }

    private void saveToken(UUID userId, UUID familyId, Instant expiresAt) {
        String tokenHash = UUID.randomUUID().toString().replace("-", "").repeat(2);
        refreshTokenRepository.save(new RefreshToken(
                UUID.randomUUID(),
                userId,
                familyId,
                tokenHash,
                expiresAt,
                expiresAt.minusSeconds(60),
                null
        ));
    }

    private int tokenCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_refresh_token", Integer.class);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
