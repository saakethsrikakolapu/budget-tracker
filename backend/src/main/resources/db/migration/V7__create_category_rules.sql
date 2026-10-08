-- "If the description contains PATTERN, use CATEGORY." Patterns are stored normalized
-- (uppercase, single spaces) and matched against descriptions normalized the same way.
CREATE TABLE category_rules (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    pattern     VARCHAR(100) NOT NULL,
    -- Deleting a category deletes the rules that point to it.
    category_id BIGINT       NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_category_rules_user_pattern UNIQUE (user_id, pattern)
);
