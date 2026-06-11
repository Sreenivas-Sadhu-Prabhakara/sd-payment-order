-- Sample data for local exploration after hydration. Idempotent.
INSERT INTO payment_order (order_id, debtor_account_ref, creditor_account_ref, amount_minor,
                           currency, remittance_info, status, status_reason,
                           received_at, submitted_at, finished_at)
VALUES
    ('PO-SEED-0001', 'CA-SEED-0001', 'SA-SEED-0001', 250000, 'INR', 'monthly transfer',
        'COMPLETED', 'settled', now() - interval '1 day', now() - interval '1 day', now() - interval '23 hours'),
    ('PO-SEED-0002', 'CA-SEED-0002', 'CA-SEED-0001', 100000, 'INR', 'invoice 7',
        'SUBMITTED', NULL, now() - interval '2 hours', now() - interval '2 hours', NULL),
    ('PO-SEED-0003', 'CA-SEED-0001', 'CA-SEED-0001', 50000, 'INR', 'oops',
        'REJECTED', 'SELF_TRANSFER', now() - interval '1 hour', NULL, now() - interval '1 hour')
ON CONFLICT (order_id) DO NOTHING;
