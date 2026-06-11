-- Payment Order service domain — Postgres schema.
-- READY TO HYDRATE, NOT YET WIRED (see platform-infra/postgres/hydrate.sh).

CREATE TABLE IF NOT EXISTS payment_order (
    order_id            VARCHAR(40)  PRIMARY KEY,
    debtor_account_ref  VARCHAR(40)  NOT NULL,
    creditor_account_ref VARCHAR(40) NOT NULL,
    amount_minor        BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency            CHAR(3)      NOT NULL,
    remittance_info     VARCHAR(140),
    status              VARCHAR(10)  NOT NULL
        CHECK (status IN ('RECEIVED','VALIDATED','REJECTED','SUBMITTED','COMPLETED','FAILED','CANCELLED')),
    status_reason       VARCHAR(140),
    received_at         TIMESTAMPTZ  NOT NULL,
    submitted_at        TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    CONSTRAINT no_self_transfer CHECK (debtor_account_ref <> creditor_account_ref),
    CONSTRAINT submitted_has_timestamp
        CHECK (status NOT IN ('SUBMITTED','COMPLETED','FAILED') OR submitted_at IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_po_status   ON payment_order (status);
CREATE INDEX IF NOT EXISTS idx_po_debtor   ON payment_order (debtor_account_ref);
CREATE INDEX IF NOT EXISTS idx_po_creditor ON payment_order (creditor_account_ref);
