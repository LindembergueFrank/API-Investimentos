ALTER TABLE investment_transaction
    ADD COLUMN ledger_order BIGINT AUTO_INCREMENT NOT NULL UNIQUE;

CREATE INDEX ix_transaction_portfolio_ledger
    ON investment_transaction (portfolio_id, ledger_order);
