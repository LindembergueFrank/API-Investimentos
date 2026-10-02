CREATE TABLE portfolio (
    id BINARY(16) NOT NULL,
    owner_id BINARY(16) NOT NULL,
    name VARCHAR(80) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_portfolio PRIMARY KEY (id),
    CONSTRAINT fk_portfolio_owner FOREIGN KEY (owner_id) REFERENCES tb_user (id)
);

CREATE INDEX ix_portfolio_owner_created_at ON portfolio (owner_id, created_at);
