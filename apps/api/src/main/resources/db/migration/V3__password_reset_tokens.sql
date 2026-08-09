-- BF-020: one-time password reset tokens. Only SHA-256 token hashes are stored.

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT password_reset_tokens_token_hash_key UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_password_reset_tokens_status
        CHECK (status IN ('ACTIVE', 'USED', 'REVOKED')),
    CONSTRAINT chk_password_reset_tokens_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT chk_password_reset_tokens_used_metadata
        CHECK (status <> 'USED' OR used_at IS NOT NULL),
    CONSTRAINT chk_password_reset_tokens_revoked_metadata
        CHECK (status <> 'REVOKED' OR (revoked_at IS NOT NULL AND revoke_reason IS NOT NULL))
);

CREATE INDEX idx_password_reset_tokens_user_status
    ON password_reset_tokens (user_id, status);

CREATE INDEX idx_password_reset_tokens_status_expires_at
    ON password_reset_tokens (status, expires_at);
