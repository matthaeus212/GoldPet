-- Prevent double-refund race condition with DB-level constraint
-- original_transaction_id should be unique (each transaction can only be refunded once)
CREATE UNIQUE INDEX IF NOT EXISTS uq_original_transaction_id
    ON gold_transactions (original_transaction_id)
    WHERE original_transaction_id IS NOT NULL;
