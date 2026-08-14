-- BF-039: local working schedules. Time-zone conversion belongs to the availability phase.
CREATE TABLE working_schedule_rules (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    weekday VARCHAR(9) NOT NULL,
    start_local_time TIME NOT NULL,
    end_local_time TIME NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT working_schedule_rules_tenant_id_id_key UNIQUE (tenant_id, id),
    CONSTRAINT fk_schedule_rule_assignment FOREIGN KEY (tenant_id, employee_id, branch_id)
        REFERENCES employee_branch_assignments (tenant_id, employee_id, branch_id) ON DELETE RESTRICT,
    CONSTRAINT chk_schedule_rule_weekday CHECK (weekday IN
        ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    CONSTRAINT chk_schedule_rule_time CHECK (start_local_time < end_local_time),
    CONSTRAINT chk_schedule_rule_dates CHECK (effective_to IS NULL OR effective_from <= effective_to)
);
CREATE INDEX idx_schedule_rules_tenant_employee_branch_weekday_dates
    ON working_schedule_rules (tenant_id, employee_id, branch_id, weekday, effective_from, effective_to);

CREATE TABLE schedule_breaks (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    schedule_rule_id UUID NOT NULL,
    start_local_time TIME NOT NULL,
    end_local_time TIME NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT schedule_breaks_tenant_id_id_key UNIQUE (tenant_id, id),
    CONSTRAINT fk_schedule_break_rule FOREIGN KEY (tenant_id, schedule_rule_id)
        REFERENCES working_schedule_rules (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT chk_schedule_break_time CHECK (start_local_time < end_local_time)
);
CREATE INDEX idx_schedule_breaks_tenant_rule_time
    ON schedule_breaks (tenant_id, schedule_rule_id, start_local_time, end_local_time);

CREATE TABLE schedule_exceptions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    exception_date DATE NOT NULL,
    type VARCHAR(32) NOT NULL,
    start_local_time TIME,
    end_local_time TIME,
    note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT schedule_exceptions_tenant_id_id_key UNIQUE (tenant_id, id),
    CONSTRAINT fk_schedule_exception_assignment FOREIGN KEY (tenant_id, employee_id, branch_id)
        REFERENCES employee_branch_assignments (tenant_id, employee_id, branch_id) ON DELETE RESTRICT,
    CONSTRAINT chk_schedule_exception_type CHECK (type IN ('TIME_OFF','WORKING_OVERRIDE')),
    CONSTRAINT chk_schedule_exception_time_pair CHECK (
        (start_local_time IS NULL AND end_local_time IS NULL) OR
        (start_local_time IS NOT NULL AND end_local_time IS NOT NULL AND start_local_time < end_local_time)
    ),
    CONSTRAINT chk_schedule_exception_semantics CHECK (
        type = 'TIME_OFF' OR (start_local_time IS NOT NULL AND end_local_time IS NOT NULL)
    ),
    CONSTRAINT chk_schedule_exception_note CHECK (note IS NULL OR length(btrim(note)) > 0)
);
CREATE INDEX idx_schedule_exceptions_tenant_employee_branch_date
    ON schedule_exceptions (tenant_id, employee_id, branch_id, exception_date, type);
