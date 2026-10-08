CREATE TABLE investment_transaction (
    id BINARY(16) NOT NULL,
    request_id BINARY(16) NOT NULL,
    portfolio_id BINARY(16) NOT NULL,
    asset_id BINARY(16) NOT NULL,
    type VARCHAR(4) NOT NULL,
    quantity DECIMAL(19, 8) NOT NULL,
    unit_price DECIMAL(19, 8) NOT NULL,
    fees DECIMAL(19, 2) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_investment_transaction PRIMARY KEY (id),
    CONSTRAINT uk_transaction_portfolio_request UNIQUE (portfolio_id, request_id),
    CONSTRAINT fk_transaction_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolio(id),
    CONSTRAINT fk_transaction_asset FOREIGN KEY (asset_id) REFERENCES asset(id),
    CONSTRAINT ck_transaction_type CHECK (type IN ('BUY', 'SELL')),
    CONSTRAINT ck_transaction_quantity CHECK (quantity > 0),
    CONSTRAINT ck_transaction_unit_price CHECK (unit_price > 0),
    CONSTRAINT ck_transaction_fees CHECK (fees >= 0)
);

CREATE INDEX ix_transaction_position ON investment_transaction (portfolio_id, asset_id, occurred_at, id);
