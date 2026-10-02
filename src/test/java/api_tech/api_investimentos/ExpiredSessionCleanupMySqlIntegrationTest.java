package api_tech.api_investimentos;

import api_tech.api_investimentos.identity.application.ExpiredSessionCleanupService;
import api_tech.api_investimentos.identity.application.RefreshTokenRepository;
import api_tech.api_investimentos.identity.application.UserRepository;
import api_tech.api_investimentos.identity.domain.RefreshToken;
import api_tech.api_investimentos.identity.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
@Import(ExpiredSessionCleanupMySqlIntegrationTest.FixedClockConfig.class)
@TestPropertySource(properties = {
        "security.refresh-token.cleanup.enabled=false",
        "security.refresh-token.cleanup.retention=P7D",
        "security.refresh-token.cleanup.batch-size=10"
})
class ExpiredSessionCleanupMySqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-27T20:00:00Z");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("apiinvestimentos")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private ExpiredSessionCleanupService cleanupService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Test
    void shouldApplyMigrationsAndCleanOnlyFullyExpiredFamilies() {
        UUID userId = saveUser();
        UUID expiredFamily = UUID.randomUUID();
        saveToken(userId, expiredFamily, NOW.minusSeconds(15 * 24 * 60 * 60L));
        saveToken(userId, expiredFamily, NOW.minusSeconds(14 * 24 * 60 * 60L));

        UUID activeFamily = UUID.randomUUID();
        saveToken(userId, activeFamily, NOW.minusSeconds(30 * 24 * 60 * 60L));
        saveToken(userId, activeFamily, NOW.plusSeconds(24 * 60 * 60L));

        assertEquals("5", latestFlywayVersion());
        assertEquals(2, cleanupIndexColumnCount());
        assertEquals(2, cleanupService.cleanupBatch());
        assertEquals(2, tokenCount());
        assertEquals(0, cleanupService.cleanupBatch());
    }

    private UUID saveUser() {
        UUID id = UUID.randomUUID();
        var savedUser = userRepository.save(new User(
                id,
                "mysql-cleanup-user",
                "mysql-cleanup-" + id + "@example.com",
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                NOW,
                NOW
        ));
        entityManager.flush();
        return savedUser.getId();
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

    private String latestFlywayVersion() {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE "
                        + "ORDER BY installed_rank DESC LIMIT 1",
                String.class
        );
    }

    private int cleanupIndexColumnCount() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'tb_refresh_token'
                  AND index_name = 'ix_refresh_token_family_expiry'
                """,
                Integer.class
        );
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
