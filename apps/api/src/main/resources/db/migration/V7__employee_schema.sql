-- BF-031/BF-033: employees and their branch assignments remain tenant-scoped in PostgreSQL.
ALTER TABLE branches ADD CONSTRAINT branches_tenant_id_id_key UNIQUE (tenant_id, id);

CREATE TABLE employees (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(254),
    bio VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT employees_tenant_code_key UNIQUE (tenant_id, code),
    CONSTRAINT employees_tenant_id_id_key UNIQUE (tenant_id, id),
    CONSTRAINT fk_employees_business FOREIGN KEY (tenant_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT chk_employees_code_format CHECK (code = upper(code) AND code ~ '^[A-Z0-9]+(?:-[A-Z0-9]+)*$'),
    CONSTRAINT chk_employees_name_not_blank CHECK (length(btrim(full_name)) > 0),
    CONSTRAINT chk_employees_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);
CREATE INDEX idx_employees_tenant_status_created ON employees (tenant_id, status, created_at, id);

CREATE TABLE employee_branch_assignments (
    tenant_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT employee_branch_assignments_key PRIMARY KEY (tenant_id, employee_id, branch_id),
    CONSTRAINT fk_assignment_employee FOREIGN KEY (tenant_id, employee_id) REFERENCES employees (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES branches (tenant_id, id) ON DELETE RESTRICT
);
CREATE INDEX idx_employee_branch_assignments_tenant_employee ON employee_branch_assignments (tenant_id, employee_id);
