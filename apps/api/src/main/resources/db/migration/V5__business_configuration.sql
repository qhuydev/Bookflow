-- BF-029: configuration owned by the business tenant.
ALTER TABLE businesses
    ADD COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'VND',
    ADD COLUMN cancellation_policy VARCHAR(20) NOT NULL DEFAULT 'FLEXIBLE',
    ADD COLUMN max_booking_advance_days INTEGER NOT NULL DEFAULT 90,
    ADD CONSTRAINT chk_businesses_currency_code CHECK (currency_code ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT chk_businesses_cancellation_policy
        CHECK (cancellation_policy IN ('FLEXIBLE', 'MODERATE', 'STRICT')),
    ADD CONSTRAINT chk_businesses_max_booking_advance_days
        CHECK (max_booking_advance_days BETWEEN 0 AND 365);
