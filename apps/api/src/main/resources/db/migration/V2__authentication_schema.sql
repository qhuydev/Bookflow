-- BF-013: authentication persistence only. Business, membership and tenant
-- schema are intentionally deferred to later tickets.

CREATE TABLE users (
    id UUID PRIMARY KEY,
    normalized_email VARCHAR(320) NOT NULL,
    password_hash TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT users_normalized_email_key UNIQUE (normalized_email),
    CONSTRAINT chk_users_normalized_email_not_blank
        CHECK (length(btrim(normalized_email)) > 0),
    CONSTRAINT chk_users_password_hash_not_blank
        CHECK (length(btrim(password_hash)) > 0),
    CONSTRAINT chk_users_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    inactivity_expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(50),
    device_label VARCHAR(200),
    user_agent_hash CHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_auth_sessions_id_user UNIQUE (id, user_id),
    CONSTRAINT chk_auth_sessions_status
        CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED', 'COMPROMISED')),
    CONSTRAINT chk_auth_sessions_expiry_order
        CHECK (inactivity_expires_at <= absolute_expires_at),
    CONSTRAINT chk_auth_sessions_last_used
        CHECK (last_used_at >= issued_at),
    CONSTRAINT chk_auth_sessions_terminal_metadata
        CHECK (
            status NOT IN ('REVOKED', 'COMPROMISED')
            OR (revoked_at IS NOT NULL AND revoke_reason IS NOT NULL)
        )
);

CREATE INDEX idx_auth_sessions_user_status
    ON auth_sessions (user_id, status);

CREATE INDEX idx_auth_sessions_absolute_expires_at
    ON auth_sessions (absolute_expires_at);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    family_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    parent_token_id UUID,
    replaced_by_token_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    inactivity_expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    rotated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_tokens_session_user
        FOREIGN KEY (family_id, user_id) REFERENCES auth_sessions (id, user_id),
    CONSTRAINT uq_refresh_tokens_id_user_family UNIQUE (id, user_id, family_id),
    CONSTRAINT fk_refresh_tokens_parent
        FOREIGN KEY (parent_token_id, user_id, family_id)
            REFERENCES refresh_tokens (id, user_id, family_id),
    CONSTRAINT fk_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by_token_id, user_id, family_id)
            REFERENCES refresh_tokens (id, user_id, family_id),
    CONSTRAINT chk_refresh_tokens_status
        CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED', 'EXPIRED', 'COMPROMISED')),
    CONSTRAINT chk_refresh_tokens_expiry_order
        CHECK (inactivity_expires_at <= absolute_expires_at),
    CONSTRAINT chk_refresh_tokens_last_used
        CHECK (last_used_at >= issued_at),
    CONSTRAINT chk_refresh_tokens_rotation_time
        CHECK (rotated_at IS NULL OR rotated_at >= issued_at),
    CONSTRAINT chk_refresh_tokens_revocation_time
        CHECK (revoked_at IS NULL OR revoked_at >= issued_at),
    CONSTRAINT chk_refresh_tokens_rotated_metadata
        CHECK (status <> 'ROTATED' OR rotated_at IS NOT NULL),
    CONSTRAINT chk_refresh_tokens_terminal_metadata
        CHECK (
            status NOT IN ('REVOKED', 'COMPROMISED')
            OR (revoked_at IS NOT NULL AND revoke_reason IS NOT NULL)
        ),
    CONSTRAINT chk_refresh_tokens_parent_not_self
        CHECK (parent_token_id IS NULL OR parent_token_id <> id),
    CONSTRAINT chk_refresh_tokens_replaced_by_not_self
        CHECK (replaced_by_token_id IS NULL OR replaced_by_token_id <> id)
);

CREATE UNIQUE INDEX uq_refresh_tokens_active_family
    ON refresh_tokens (family_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);

CREATE INDEX idx_refresh_tokens_family_status
    ON refresh_tokens (family_id, status);

CREATE INDEX idx_refresh_tokens_absolute_expires_at
    ON refresh_tokens (absolute_expires_at);
