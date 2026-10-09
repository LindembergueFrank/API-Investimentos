CREATE INDEX ix_transaction_timeline
    ON investment_transaction (portfolio_id, occurred_at, created_at, id);
