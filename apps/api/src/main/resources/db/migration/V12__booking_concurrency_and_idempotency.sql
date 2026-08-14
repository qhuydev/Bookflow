-- BF-045: PostgreSQL is the final concurrency guard for employee bookings.
-- The partial predicate mirrors BookingStatus.occupiesSlot() and is verified by integration tests.

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings
    ADD CONSTRAINT no_overlapping_active_employee_bookings
    EXCLUDE USING gist (
        tenant_id WITH =,
        employee_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    )
    WHERE (
        employee_id IS NOT NULL
        AND status IN (
            'PENDING_PAYMENT',
            'PENDING_CONFIRMATION',
            'CONFIRMED',
            'IN_PROGRESS'
        )
    );

CREATE TABLE booking_idempotency_keys (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    booking_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,

    CONSTRAINT booking_idempotency_keys_tenant_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_booking_idempotency_business FOREIGN KEY (tenant_id)
        REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_idempotency_booking FOREIGN KEY (tenant_id, booking_id)
        REFERENCES bookings (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_booking_idempotency_key CHECK (
        length(idempotency_key) BETWEEN 8 AND 200
    ),
    CONSTRAINT chk_booking_idempotency_fingerprint CHECK (
        request_fingerprint ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_booking_idempotency_completion CHECK (
        (booking_id IS NULL AND completed_at IS NULL)
        OR (booking_id IS NOT NULL AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_booking_idempotency_tenant_booking
    ON booking_idempotency_keys (tenant_id, booking_id)
    WHERE booking_id IS NOT NULL;
