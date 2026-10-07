-- Duplicate detection. A fingerprint identifies "the same purchase" across overlapping exports;
-- occurrence numbers the identical copies (two $4.75 coffees on one day are occurrences 1 and 2).
ALTER TABLE transactions ADD COLUMN fingerprint VARCHAR(64);
ALTER TABLE transactions ADD COLUMN occurrence  INTEGER;

-- Backfill rows imported before this migration. This formula must match
-- TransactionFingerprint.of() in Java (a test checks that it does):
--   sha256( date | amount | description with whitespace collapsed, trimmed, uppercased )
UPDATE transactions
SET fingerprint = encode(sha256(convert_to(
        transaction_date::text || '|' || amount::text || '|'
            || upper(btrim(regexp_replace(description, '\s+', ' ', 'g'))),
        'UTF8')), 'hex');

WITH numbered AS (
    SELECT id, row_number() OVER (PARTITION BY account_id, fingerprint ORDER BY id) AS n
    FROM transactions
)
UPDATE transactions t
SET occurrence = numbered.n
FROM numbered
WHERE t.id = numbered.id;

ALTER TABLE transactions ALTER COLUMN fingerprint SET NOT NULL;
ALTER TABLE transactions ALTER COLUMN occurrence  SET NOT NULL;

-- The database itself refuses a second copy of "occurrence n of this purchase in this account",
-- even if two uploads of the same file run at the same moment. Also speeds up duplicate lookups.
ALTER TABLE transactions
    ADD CONSTRAINT uq_transactions_account_fingerprint_occurrence UNIQUE (account_id, fingerprint, occurrence);

-- How many rows of an upload were skipped as already imported.
ALTER TABLE import_batches ADD COLUMN skipped_count INTEGER NOT NULL DEFAULT 0;
