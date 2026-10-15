-- Stage 6: categories suggested by the machine-learning model (CategoryModel.java) are stored with
-- source SUGGESTED, so the app can label them and they rank below every other source.
ALTER TABLE transactions DROP CONSTRAINT ck_transactions_category_source;
ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_category_source
        CHECK (category_source IN ('BANK', 'RULE', 'MANUAL', 'KEYWORD', 'SUGGESTED'));
