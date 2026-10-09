-- More bank category labels -> default categories, from labels other banks reportedly use
-- (Chase, Discover, Apple Card, American Express). Unknown labels simply stay Uncategorized, so
-- an outdated or wrong label here can't miscategorize anything; it just doesn't help.
-- American Express uses "Group-Subgroup" labels; CategoryAssigner also tries the part before "-".
INSERT INTO bank_category_mappings (bank_label, category_name) VALUES
    -- Chase
    ('food & drink', 'Food & Dining'),
    ('gas', 'Transportation'),
    ('automotive', 'Transportation'),
    ('shopping', 'Shopping'),
    ('travel', 'Travel'),
    ('bills & utilities', 'Bills & Utilities'),
    ('health & wellness', 'Health & Personal Care'),
    ('fees & adjustments', 'Fees & Interest'),
    -- Discover
    ('restaurants', 'Food & Dining'),
    ('supermarkets', 'Groceries'),
    ('gasoline', 'Transportation'),
    ('travel/ entertainment', 'Travel'),
    ('department stores', 'Shopping'),
    ('medical services', 'Health & Personal Care'),
    ('payments and credits', 'Payments & Transfers'),
    -- Apple Card
    ('transportation', 'Transportation'),
    ('medical', 'Health & Personal Care'),
    ('payment', 'Payments & Transfers'),
    -- American Express groups (matched on the part before "-")
    ('restaurant', 'Food & Dining'),
    ('merchandise & supplies-groceries', 'Groceries'),
    ('merchandise & supplies', 'Shopping'),
    ('communications', 'Bills & Utilities')
ON CONFLICT (bank_label) DO NOTHING;

-- Categorize existing uncategorized transactions with the new labels (exact matches).
UPDATE transactions t
SET category_id = c.id,
    category_source = 'BANK'
FROM bank_category_mappings m
JOIN categories c ON lower(c.name) = lower(m.category_name)
WHERE m.bank_label = lower(t.bank_category)
  AND c.user_id = t.user_id
  AND t.category_id IS NULL
  AND t.category_source IS NULL;
