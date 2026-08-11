-- BF-030: branches are business-owned resources. tenant_id always equals businesses.id.
CREATE TABLE branches (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    address_line1 VARCHAR(200) NOT NULL,
    address_line2 VARCHAR(200),
    ward VARCHAR(100),
    district VARCHAR(100),
    city VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20),
    country_code CHAR(2) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(254),
    time_zone VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT branches_tenant_code_key UNIQUE (tenant_id, code),
    CONSTRAINT fk_branches_business FOREIGN KEY (tenant_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT chk_branches_code_format CHECK (code = upper(code) AND code ~ '^[A-Z0-9]+(?:-[A-Z0-9]+)*$'),
    CONSTRAINT chk_branches_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT chk_branches_address_line1_not_blank CHECK (length(btrim(address_line1)) > 0),
    CONSTRAINT chk_branches_city_not_blank CHECK (length(btrim(city)) > 0),
    CONSTRAINT chk_branches_country_code CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT chk_branches_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_branches_time_zone_iana_shape CHECK (
        time_zone = 'UTC' OR time_zone ~ '^[A-Za-z]+(?:[+_-][A-Za-z0-9]+)*(?:/[A-Za-z0-9+_-]+)+$'
    )
);

CREATE INDEX idx_branches_tenant_status_created
    ON branches (tenant_id, status, created_at, id);
