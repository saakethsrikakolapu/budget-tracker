-- How a transaction got its category:
--   BANK   = mapped from the bank's own label (e.g. Capital One "Dining" -> "Food & Dining")
--   RULE   = matched one of the user's rules (Stage 2, piece 3)
--   MANUAL = the user picked it; automation never overwrites these
-- NULL = never categorized automatically or by hand.
ALTER TABLE transactions
    ADD COLUMN category_source VARCHAR(10)
        CONSTRAINT ck_transactions_category_source CHECK (category_source IN ('BANK', 'RULE', 'MANUAL'));

-- Bank label -> default category name. Matched case-insensitively against each user's
-- categories; if the user renamed or deleted that category, the transaction stays Uncategorized.
-- Vague labels ("Other", "Other Services", "Professional Services") are deliberately not mapped,
-- so they show up as Uncategorized for the user (or a rule) to sort out.
CREATE TABLE bank_category_mappings (
    bank_label    VARCHAR(100) PRIMARY KEY, -- stored lowercase
    category_name VARCHAR(50)  NOT NULL
);

INSERT INTO bank_category_mappings (bank_label, category_name) VALUES
    ('airfare', 'Travel'),
    ('car rental', 'Travel'),
    ('lodging', 'Travel'),
    ('other travel', 'Travel'),
    ('dining', 'Food & Dining'),
    ('entertainment', 'Entertainment'),
    ('fee/interest charge', 'Fees & Interest'),
    ('gas/automotive', 'Transportation'),
    ('grocery', 'Groceries'),
    ('groceries', 'Groceries'),
    ('health care', 'Health & Personal Care'),
    ('insurance', 'Bills & Utilities'),
    ('internet', 'Bills & Utilities'),
    ('phone/cable', 'Bills & Utilities'),
    ('utilities', 'Bills & Utilities'),
    ('merchandise', 'Shopping'),
    ('payment/credit', 'Payments & Transfers'),
    ('education', 'Education');

-- Categorize everything imported before this migration, using the same mapping.
UPDATE transactions t
SET category_id = c.id,
    category_source = 'BANK'
FROM bank_category_mappings m
JOIN categories c ON lower(c.name) = lower(m.category_name)
WHERE m.bank_label = lower(t.bank_category)
  AND c.user_id = t.user_id
  AND t.category_id IS NULL;
