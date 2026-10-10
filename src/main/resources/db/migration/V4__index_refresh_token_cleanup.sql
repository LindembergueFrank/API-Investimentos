CREATE INDEX ix_refresh_token_family_expiry ON tb_refresh_token (family_id, expires_at);
