-- A monthly spending limit for one category, e.g. "Food & Dining: $200 per month".
-- It applies to every month (set once, not re-entered monthly).
CREATE TABLE budgets (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- One budget per category; deleting the category deletes its budget.
    category_id   BIGINT        NOT NULL UNIQUE REFERENCES categories (id) ON DELETE CASCADE,
    monthly_limit NUMERIC(12,2) NOT NULL CONSTRAINT ck_budgets_limit_positive CHECK (monthly_limit > 0),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_budgets_user ON budgets (user_id);
