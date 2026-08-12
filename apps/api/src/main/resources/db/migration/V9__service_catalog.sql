-- BF-034/BF-035: tenant-scoped service catalog and branch/employee availability assignments.
CREATE TABLE services (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    price NUMERIC(19,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    duration_minutes INTEGER NOT NULL,
    buffer_before_minutes INTEGER NOT NULL DEFAULT 0,
    buffer_after_minutes INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT services_tenant_id_id_key UNIQUE (tenant_id,id),
    CONSTRAINT chk_services_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT chk_services_price CHECK (price > 0),
    CONSTRAINT chk_services_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_services_duration CHECK (duration_minutes > 0),
    CONSTRAINT chk_services_buffers CHECK (buffer_before_minutes >= 0 AND buffer_after_minutes >= 0),
    CONSTRAINT chk_services_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);
CREATE INDEX idx_services_tenant_status_created ON services(tenant_id,status,created_at,id);

CREATE TABLE branch_services (
    tenant_id UUID NOT NULL, branch_id UUID NOT NULL, service_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id,branch_id,service_id),
    FOREIGN KEY (tenant_id,branch_id) REFERENCES branches(tenant_id,id) ON DELETE RESTRICT,
    FOREIGN KEY (tenant_id,service_id) REFERENCES services(tenant_id,id) ON DELETE RESTRICT
);
CREATE INDEX idx_branch_services_tenant_service ON branch_services(tenant_id,service_id);

CREATE TABLE employee_services (
    tenant_id UUID NOT NULL, employee_id UUID NOT NULL, service_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id,employee_id,service_id),
    FOREIGN KEY (tenant_id,employee_id) REFERENCES employees(tenant_id,id) ON DELETE RESTRICT,
    FOREIGN KEY (tenant_id,service_id) REFERENCES services(tenant_id,id) ON DELETE RESTRICT
);
CREATE INDEX idx_employee_services_tenant_service ON employee_services(tenant_id,service_id);
