-- BF-044: tenant-scoped booking aggregate foundation.
-- Double-booking exclusion constraints and public booking APIs are intentionally deferred to BF-045.

CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    employee_id UUID,
    customer_user_id UUID,
    customer_name VARCHAR(200) NOT NULL,
    customer_email VARCHAR(320),
    customer_phone VARCHAR(30),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT bookings_tenant_id_id_key UNIQUE (tenant_id, id),
    CONSTRAINT fk_bookings_business FOREIGN KEY (tenant_id)
        REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_branch FOREIGN KEY (tenant_id, branch_id)
        REFERENCES branches (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_employee FOREIGN KEY (tenant_id, employee_id)
        REFERENCES employees (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_customer_user FOREIGN KEY (customer_user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_bookings_customer_name CHECK (length(btrim(customer_name)) > 0),
    CONSTRAINT chk_bookings_customer_reference CHECK (
        customer_user_id IS NOT NULL OR customer_email IS NOT NULL OR customer_phone IS NOT NULL
    ),
    CONSTRAINT chk_bookings_time CHECK (start_at < end_at),
    CONSTRAINT chk_bookings_status CHECK (status IN (
        'PENDING_PAYMENT', 'PENDING_CONFIRMATION', 'CONFIRMED', 'IN_PROGRESS',
        'COMPLETED', 'CANCELLED_BY_CUSTOMER', 'CANCELLED_BY_BUSINESS', 'NO_SHOW', 'EXPIRED'
    )),
    CONSTRAINT chk_bookings_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_bookings_total_amount CHECK (total_amount >= 0),
    CONSTRAINT chk_bookings_hold_expiry CHECK (
        status NOT IN ('PENDING_PAYMENT', 'PENDING_CONFIRMATION') OR expires_at IS NOT NULL
    ),
    CONSTRAINT chk_bookings_expiry_time CHECK (expires_at IS NULL OR expires_at > created_at),
    CONSTRAINT chk_bookings_timestamps CHECK (updated_at >= created_at)
);

CREATE INDEX idx_bookings_tenant_branch_start
    ON bookings (tenant_id, branch_id, start_at, id);
CREATE INDEX idx_bookings_tenant_employee_time_status
    ON bookings (tenant_id, employee_id, start_at, end_at, status)
    WHERE employee_id IS NOT NULL;
CREATE INDEX idx_bookings_tenant_status_start
    ON bookings (tenant_id, status, start_at, id);
CREATE INDEX idx_bookings_tenant_customer_created
    ON bookings (tenant_id, customer_user_id, created_at, id)
    WHERE customer_user_id IS NOT NULL;

CREATE TABLE booking_items (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    booking_id UUID NOT NULL,
    service_id UUID NOT NULL,
    position INTEGER NOT NULL,
    service_name_snapshot VARCHAR(200) NOT NULL,
    price_snapshot NUMERIC(19,2) NOT NULL,
    currency_snapshot CHAR(3) NOT NULL,
    duration_minutes_snapshot INTEGER NOT NULL,
    buffer_before_minutes_snapshot INTEGER NOT NULL DEFAULT 0,
    buffer_after_minutes_snapshot INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT booking_items_tenant_id_id_key UNIQUE (tenant_id, id),
    CONSTRAINT booking_items_booking_position_key UNIQUE (tenant_id, booking_id, position),
    CONSTRAINT fk_booking_items_booking FOREIGN KEY (tenant_id, booking_id)
        REFERENCES bookings (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_items_service FOREIGN KEY (tenant_id, service_id)
        REFERENCES services (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_booking_items_position CHECK (position >= 0),
    CONSTRAINT chk_booking_items_name CHECK (length(btrim(service_name_snapshot)) > 0),
    CONSTRAINT chk_booking_items_price CHECK (price_snapshot >= 0),
    CONSTRAINT chk_booking_items_currency CHECK (currency_snapshot ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_booking_items_duration CHECK (duration_minutes_snapshot > 0),
    CONSTRAINT chk_booking_items_buffers CHECK (
        buffer_before_minutes_snapshot >= 0 AND buffer_after_minutes_snapshot >= 0
    )
);

CREATE INDEX idx_booking_items_tenant_booking
    ON booking_items (tenant_id, booking_id, position);
CREATE INDEX idx_booking_items_tenant_service
    ON booking_items (tenant_id, service_id, booking_id);

CREATE TABLE booking_status_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    booking_id UUID NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    actor_user_id UUID,
    reason VARCHAR(500),
    changed_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT booking_status_history_tenant_id_id_key UNIQUE (tenant_id, id),
    CONSTRAINT fk_booking_status_history_booking FOREIGN KEY (tenant_id, booking_id)
        REFERENCES bookings (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_status_history_actor FOREIGN KEY (actor_user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_booking_history_from_status CHECK (from_status IS NULL OR from_status IN (
        'PENDING_PAYMENT', 'PENDING_CONFIRMATION', 'CONFIRMED', 'IN_PROGRESS',
        'COMPLETED', 'CANCELLED_BY_CUSTOMER', 'CANCELLED_BY_BUSINESS', 'NO_SHOW', 'EXPIRED'
    )),
    CONSTRAINT chk_booking_history_to_status CHECK (to_status IN (
        'PENDING_PAYMENT', 'PENDING_CONFIRMATION', 'CONFIRMED', 'IN_PROGRESS',
        'COMPLETED', 'CANCELLED_BY_CUSTOMER', 'CANCELLED_BY_BUSINESS', 'NO_SHOW', 'EXPIRED'
    )),
    CONSTRAINT chk_booking_history_transition CHECK (from_status IS NULL OR from_status <> to_status),
    CONSTRAINT chk_booking_history_reason CHECK (reason IS NULL OR length(btrim(reason)) > 0)
);

CREATE INDEX idx_booking_history_tenant_booking_changed
    ON booking_status_history (tenant_id, booking_id, changed_at, id);
