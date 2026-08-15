-- BF-046: bounded expiry scans and auditable atomic reschedules.

CREATE INDEX idx_bookings_expiry_candidates
    ON bookings (expires_at, id)
    WHERE status IN ('PENDING_PAYMENT', 'PENDING_CONFIRMATION');

CREATE TABLE booking_reschedule_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    booking_id UUID NOT NULL,
    old_employee_id UUID,
    new_employee_id UUID,
    old_start_at TIMESTAMPTZ NOT NULL,
    old_end_at TIMESTAMPTZ NOT NULL,
    new_start_at TIMESTAMPTZ NOT NULL,
    new_end_at TIMESTAMPTZ NOT NULL,
    actor_user_id UUID NOT NULL,
    reason VARCHAR(500),
    changed_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT booking_reschedule_history_tenant_id_id_key UNIQUE (tenant_id, id),
    CONSTRAINT fk_booking_reschedule_history_booking FOREIGN KEY (tenant_id, booking_id)
        REFERENCES bookings (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_reschedule_history_old_employee FOREIGN KEY (tenant_id, old_employee_id)
        REFERENCES employees (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_reschedule_history_new_employee FOREIGN KEY (tenant_id, new_employee_id)
        REFERENCES employees (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_reschedule_history_actor FOREIGN KEY (actor_user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_booking_reschedule_old_time CHECK (old_start_at < old_end_at),
    CONSTRAINT chk_booking_reschedule_new_time CHECK (new_start_at < new_end_at),
    CONSTRAINT chk_booking_reschedule_changed CHECK (
        old_start_at <> new_start_at
        OR old_end_at <> new_end_at
        OR old_employee_id IS DISTINCT FROM new_employee_id
    ),
    CONSTRAINT chk_booking_reschedule_reason CHECK (reason IS NULL OR length(btrim(reason)) > 0)
);

CREATE INDEX idx_booking_reschedule_history_tenant_booking_changed
    ON booking_reschedule_history (tenant_id, booking_id, changed_at, id);
