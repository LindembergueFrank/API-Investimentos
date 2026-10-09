package api_tech.api_investimentos;

import api_tech.api_investimentos.identity.application.ExpiredSessionCleanupService;
import api_tech.api_investimentos.identity.application.RefreshTokenRepository;
import api_tech.api_investimentos.identity.application.UserRepository;
import api_tech.api_investimentos.identity.domain.RefreshToken;
import api_tech.api_investimentos.identity.domain.User;
import api_tech.api_investimentos.asset.application.AssetRepository;
import api_tech.api_investimentos.asset.domain.Asset;
import api_tech.api_investimentos.asset.domain.AssetMarket;
import api_tech.api_investimentos.asset.domain.AssetType;
import api_tech.api_investimentos.portfolio.application.PortfolioNotFoundException;
import api_tech.api_investimentos.portfolio.application.PortfolioRepository;
import api_tech.api_investimentos.portfolio.domain.Portfolio;
import api_tech.api_investimentos.position.application.PositionService;
import api_tech.api_investimentos.transaction.application.TransactionRepository;
import api_tech.api_investimentos.transaction.domain.InvestmentTransaction;
import api_tech.api_investimentos.transaction.domain.TransactionType;
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
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PositionService positionService;

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

        assertEquals("10", latestFlywayVersion());
        assertEquals(2, cleanupIndexColumnCount());
        assertEquals(2, ledgerIndexColumnCount());
        assertEquals(2, cleanupService.cleanupBatch());
        assertEquals(2, tokenCount());
        assertEquals(0, cleanupService.cleanupBatch());
    }

    @Test
    void shouldDerivePositionsInMonotonicLedgerOrderAndIsolateTheOwner() {
        UUID ownerId = saveUser();
        UUID strangerId = saveUser();
        UUID portfolioId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        portfolioRepository.save(new Portfolio(portfolioId, ownerId, "MySQL portfolio"));
        assetRepository.save(new Asset(assetId, AssetMarket.B3, "MYSQL3", AssetType.STOCK, "MySQL asset"));

        saveTransaction(portfolioId, assetId, TransactionType.BUY, "10", "20", "2", NOW.plusSeconds(60));
        saveTransaction(portfolioId, assetId, TransactionType.SELL, "10", "25", "1", NOW.minusSeconds(60));
        saveTransaction(portfolioId, assetId, TransactionType.BUY, "5", "30", "3", NOW);

        var ledgerOrders = jdbcTemplate.queryForList(
                "SELECT ledger_order FROM investment_transaction ORDER BY ledger_order",
                Long.class
        );
        assertEquals(3, ledgerOrders.size());
        assertEquals(ledgerOrders.getFirst() + 1, ledgerOrders.get(1));
        assertEquals(ledgerOrders.get(1) + 1, ledgerOrders.get(2));

        var positions = positionService.listByPortfolio(portfolioId, ownerId);
        assertEquals(1, positions.size());
        assertEquals(assetId, positions.getFirst().assetId());
        assertEquals(new BigDecimal("5.00000000"), positions.getFirst().quantity());
        assertEquals(new BigDecimal("30.60000000"), positions.getFirst().averagePrice());
        assertEquals(new BigDecimal("153.00000000"), positions.getFirst().totalCost());
        assertThrows(PortfolioNotFoundException.class,
                () -> positionService.listByPortfolio(portfolioId, strangerId));
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

    private int ledgerIndexColumnCount() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'investment_transaction'
                  AND index_name = 'ix_transaction_portfolio_ledger'
                """,
                Integer.class
        );
    }

    private void saveTransaction(UUID portfolioId, UUID assetId, TransactionType type,
                                 String quantity, String unitPrice, String fees, Instant occurredAt) {
        transactionRepository.save(new InvestmentTransaction(
                UUID.randomUUID(), UUID.randomUUID(), portfolioId, assetId, type,
                new BigDecimal(quantity), new BigDecimal(unitPrice), new BigDecimal(fees), occurredAt
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
