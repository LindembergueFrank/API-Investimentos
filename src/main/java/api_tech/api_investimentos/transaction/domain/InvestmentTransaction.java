package api_tech.api_investimentos.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "investment_transaction")
public class InvestmentTransaction {
    @Id private UUID id;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "portfolio_id", nullable = false) private UUID portfolioId;
    @Column(name = "asset_id", nullable = false) private UUID assetId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 4) private TransactionType type;
    @Column(nullable = false, precision = 19, scale = 8) private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 8) private BigDecimal unitPrice;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal fees;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "ledger_order", nullable = false, insertable = false, updatable = false) private Long ledgerOrder;

    protected InvestmentTransaction() {}

    public InvestmentTransaction(UUID id, UUID requestId, UUID portfolioId, UUID assetId, TransactionType type,
                                 BigDecimal quantity, BigDecimal unitPrice, BigDecimal fees, Instant occurredAt) {
        this.id = id;
        this.requestId = requestId;
        this.portfolioId = portfolioId;
        this.assetId = assetId;
        this.type = type;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.fees = fees;
        this.occurredAt = occurredAt;
    }

    public boolean hasSameContent(TransactionType candidateType, UUID candidateAssetId, BigDecimal candidateQuantity,
                                  BigDecimal candidateUnitPrice, BigDecimal candidateFees, Instant candidateOccurredAt) {
        return type == candidateType && assetId.equals(candidateAssetId)
                && quantity.compareTo(candidateQuantity) == 0 && unitPrice.compareTo(candidateUnitPrice) == 0
                && fees.compareTo(candidateFees) == 0 && occurredAt.equals(candidateOccurredAt);
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public UUID getPortfolioId() { return portfolioId; }
    public UUID getAssetId() { return assetId; }
    public TransactionType getType() { return type; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getFees() { return fees; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getLedgerOrder() { return ledgerOrder; }
}
