-- BF-032: optional link between a tenant employee and an already registered user.
ALTER TABLE employees ADD COLUMN user_id UUID;
ALTER TABLE employees ADD CONSTRAINT fk_employees_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT;
ALTER TABLE employees ADD CONSTRAINT employees_tenant_user_key UNIQUE (tenant_id, user_id);
CREATE INDEX idx_employees_tenant_user ON employees (tenant_id, user_id) WHERE user_id IS NOT NULL;
