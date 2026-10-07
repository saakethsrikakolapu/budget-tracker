-- A user's bank or card account, e.g. "Capital One Quicksilver".
CREATE TABLE accounts (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_accounts_user_name UNIQUE (user_id, name)
);

-- One uploaded statement file. Lets an import be listed and undone later.
CREATE TABLE import_batches (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id BIGINT       NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    file_name  VARCHAR(255) NOT NULL,
    row_count  INTEGER      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_import_batches_user ON import_batches (user_id);

CREATE TABLE transactions (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id       BIGINT        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    import_batch_id  BIGINT        NOT NULL REFERENCES import_batches (id) ON DELETE CASCADE,
    transaction_date DATE          NOT NULL,
    posted_date      DATE,
    description      VARCHAR(500)  NOT NULL,
    -- Exact decimal, never floating point. Negative = money spent, positive = refund or payment.
    amount           NUMERIC(12,2) NOT NULL,
    -- The bank's own category label, kept as-is (e.g. "Merchandise").
    bank_category    VARCHAR(100),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- The transaction list will be filtered by user and sorted/filtered by date.
CREATE INDEX idx_transactions_user_date ON transactions (user_id, transaction_date);
-- Undoing an import deletes by batch.
CREATE INDEX idx_transactions_import_batch ON transactions (import_batch_id);
