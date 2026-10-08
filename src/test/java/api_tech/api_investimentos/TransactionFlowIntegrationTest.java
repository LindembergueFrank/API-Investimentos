package api_tech.api_investimentos;

import api_tech.api_investimentos.identity.api.CreateUserDto;
import api_tech.api_investimentos.identity.api.LoginRequest;
import api_tech.api_investimentos.portfolio.api.CreatePortfolioRequest;
import api_tech.api_investimentos.transaction.api.CreateTransactionRequest;
import api_tech.api_investimentos.transaction.domain.TransactionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:transactions;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class TransactionFlowIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateIdempotentlyRejectOversellingAndHideForeignPortfolio() throws Exception {
        String owner = registerAndLogin("transaction-owner");
        String stranger = registerAndLogin("transaction-stranger");
        UUID portfolioId = createPortfolio(owner);
        UUID assetId = createAsset();
        UUID requestId = UUID.randomUUID();
        Instant occurredAt = Instant.now().minus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS);
        var buy = request(requestId, portfolioId, assetId, TransactionType.BUY, "10.12345678", occurredAt);

        var first = mockMvc.perform(post("/v1/transactions").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(buy)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.quantity").value(10.12345678))
                .andReturn();
        String transactionId = objectMapper.readTree(first.getResponse().getContentAsByteArray()).get("id").asText();

        mockMvc.perform(post("/v1/transactions").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(buy)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(transactionId));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM investment_transaction", Integer.class));

        var conflictingRetry = request(requestId, portfolioId, assetId, TransactionType.BUY, "11", occurredAt);
        perform(owner, conflictingRetry).andExpect(status().isConflict());

        var excessiveSale = request(UUID.randomUUID(), portfolioId, assetId, TransactionType.SELL, "10.12345679", occurredAt);
        perform(owner, excessiveSale).andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        perform(stranger, request(UUID.randomUUID(), portfolioId, assetId, TransactionType.BUY, "1", occurredAt))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldSerializeConcurrentSalesAndPreserveNonNegativePosition() throws Exception {
        String owner = registerAndLogin("concurrent-owner");
        UUID portfolioId = createPortfolio(owner);
        UUID assetId = createAsset();
        Instant occurredAt = Instant.now().minus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS);
        perform(owner, request(UUID.randomUUID(), portfolioId, assetId, TransactionType.BUY, "10", occurredAt))
                .andExpect(status().isCreated());

        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(
                    executor.submit(() -> submitAfter(gate, owner, request(UUID.randomUUID(), portfolioId, assetId, TransactionType.SELL, "7", occurredAt))),
                    executor.submit(() -> submitAfter(gate, owner, request(UUID.randomUUID(), portfolioId, assetId, TransactionType.SELL, "7", occurredAt)))
            );
            gate.countDown();
            var statuses = tasks.stream().map(task -> {
                try { return task.get(); } catch (Exception exception) { throw new RuntimeException(exception); }
            }).sorted().toList();
            assertEquals(List.of(201, 409), statuses);
        }
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM investment_transaction WHERE portfolio_id = ?", Integer.class, portfolioId));
    }

    private int submitAfter(CountDownLatch gate, String token, CreateTransactionRequest request) throws Exception {
        gate.await();
        return mockMvc.perform(post("/v1/transactions").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andReturn().getResponse().getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions perform(String token, CreateTransactionRequest request) throws Exception {
        return mockMvc.perform(post("/v1/transactions").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)));
    }

    private CreateTransactionRequest request(UUID requestId, UUID portfolioId, UUID assetId, TransactionType type,
                                             String quantity, Instant occurredAt) {
        return new CreateTransactionRequest(requestId, portfolioId, assetId, type, new BigDecimal(quantity),
                new BigDecimal("31.12345678"), new BigDecimal("1.25"), occurredAt);
    }

    private UUID createAsset() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO asset (id, market, ticker, type, name, created_at) VALUES (?, 'B3', ?, 'STOCK', 'Test asset', CURRENT_TIMESTAMP)",
                id, "T" + id.toString().replace("-", "").substring(0, 7).toUpperCase());
        return id;
    }

    private UUID createPortfolio(String token) throws Exception {
        var result = mockMvc.perform(post("/v1/portfolios").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(new CreatePortfolioRequest("Main"))))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText());
    }

    private String registerAndLogin(String name) throws Exception {
        String email = name + "-" + UUID.randomUUID() + "@example.com";
        String password = "strong-password";
        mockMvc.perform(post("/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new CreateUserDto(name, email, password))))
                .andExpect(status().isCreated());
        var result = mockMvc.perform(post("/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new LoginRequest(email, password))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
