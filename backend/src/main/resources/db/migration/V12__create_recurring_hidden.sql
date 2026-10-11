-- Merchants the user said are NOT subscriptions (e.g. a coffee shop they visit like clockwork).
-- Recurring charges themselves aren't stored: they're detected from transactions on each request,
-- so they're always up to date with imports, undos, and new statements.
CREATE TABLE recurring_hidden (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- See MerchantKey: e.g. "NETFLIX COM".
    merchant_key VARCHAR(200) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_recurring_hidden_user_key UNIQUE (user_id, merchant_key)
);
