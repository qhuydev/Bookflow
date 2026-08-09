-- BF-023: global business tenants and their user memberships.
-- Business-owned tables added later use tenant_id = businesses.id.

CREATE TABLE businesses (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    business_type VARCHAR(30) NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT businesses_slug_key UNIQUE (slug),
    CONSTRAINT chk_businesses_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT chk_businesses_slug_format
        CHECK (slug = lower(slug) AND slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT chk_businesses_type
        CHECK (business_type IN ('SALON', 'SPA', 'CLINIC', 'TUTORING_CENTER', 'STUDIO', 'OTHER')),
    CONSTRAINT chk_businesses_time_zone_iana_shape
        CHECK (
            time_zone = 'UTC'
            OR time_zone ~ '^[A-Za-z]+(?:[+_-][A-Za-z0-9]+)*(?:/[A-Za-z0-9+_-]+)+$'
        ),
    CONSTRAINT chk_businesses_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE INDEX idx_businesses_status
    ON businesses (status);

CREATE TABLE business_memberships (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    revoked_at TIMESTAMPTZ,
    revoked_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT business_memberships_user_tenant_key UNIQUE (user_id, tenant_id),
    CONSTRAINT fk_business_memberships_business
        FOREIGN KEY (tenant_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_business_memberships_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_business_memberships_revoked_by
        FOREIGN KEY (revoked_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_business_memberships_role
        CHECK (role IN ('OWNER', 'ADMIN', 'STAFF')),
    CONSTRAINT chk_business_memberships_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT chk_business_memberships_revocation_metadata
        CHECK (status <> 'REVOKED' OR revoked_at IS NOT NULL),
    CONSTRAINT chk_business_memberships_revocation_time
        CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX idx_business_memberships_tenant_status_role
    ON business_memberships (tenant_id, status, role);

CREATE INDEX idx_business_memberships_user_status
    ON business_memberships (user_id, status);
