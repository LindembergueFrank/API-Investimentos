CREATE TABLE asset (
    id BINARY(16) NOT NULL,
    market VARCHAR(10) NOT NULL,
    ticker VARCHAR(12) NOT NULL,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_asset PRIMARY KEY (id),
    CONSTRAINT uk_asset_market_ticker UNIQUE (market, ticker),
    CONSTRAINT ck_asset_market CHECK (market IN ('B3')),
    CONSTRAINT ck_asset_type CHECK (type IN ('STOCK', 'ETF', 'FII'))
);
