CREATE TABLE tb_refresh_token (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    family_id BINARY(16) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6),
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES tb_user (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_token_user ON tb_refresh_token (user_id);
CREATE INDEX ix_refresh_token_family ON tb_refresh_token (family_id);
