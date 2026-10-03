package api_tech.api_investimentos.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asset")
public class Asset {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AssetMarket market;
    @Column(nullable = false, length = 12)
    private String ticker;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetType type;
    @Column(nullable = false, length = 120)
    private String name;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Asset() {
    }

    public Asset(UUID id, AssetMarket market, String ticker, AssetType type, String name) {
        this.id = id;
        this.market = market;
        this.ticker = ticker;
        this.type = type;
        this.name = name;
    }

    public UUID getId() { return id; }
    public AssetMarket getMarket() { return market; }
    public String getTicker() { return ticker; }
    public AssetType getType() { return type; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}
