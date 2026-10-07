-- Each user's own spending categories ("Food & Dining", "Textbooks", ...).
CREATE TABLE categories (
    id                 BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name               VARCHAR(50) NOT NULL,
    -- False for categories like "Payments & Transfers": paying your card bill isn't new spending
    -- (the purchases were already counted), so including it would double-count.
    counts_as_spending BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Unique per user, ignoring case: "Food" and "food" can't both exist.
CREATE UNIQUE INDEX uq_categories_user_lower_name ON categories (user_id, lower(name));

-- A transaction's category. NULL means "Uncategorized" (there is no Uncategorized row).
-- Deleting a category makes its transactions Uncategorized instead of deleting them.
ALTER TABLE transactions
    ADD COLUMN category_id BIGINT REFERENCES categories (id) ON DELETE SET NULL;
CREATE INDEX idx_transactions_category ON transactions (category_id);

-- Give every existing user the default categories. New users get them at signup from
-- DefaultCategories.java; keep these two lists the same.
INSERT INTO categories (user_id, name, counts_as_spending)
SELECT u.id, d.name, d.counts_as_spending
FROM users u
CROSS JOIN (VALUES
    ('Food & Dining', TRUE),
    ('Groceries', TRUE),
    ('Shopping', TRUE),
    ('Transportation', TRUE),
    ('Travel', TRUE),
    ('Entertainment', TRUE),
    ('Subscriptions', TRUE),
    ('Bills & Utilities', TRUE),
    ('Education', TRUE),
    ('Health & Personal Care', TRUE),
    ('Fees & Interest', TRUE),
    ('Income', FALSE),
    ('Payments & Transfers', FALSE),
    ('Other', TRUE)
) AS d (name, counts_as_spending);
