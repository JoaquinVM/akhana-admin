-- CAJ-09: on closing, the difference between counted and expected cash is recorded as an adjustment in Caja,
-- with the system category Ajuste (FIN-05): an income for a surplus and an expense for a shortage.
-- Existing categories named Ajuste are reused.
ALTER TABLE financial_categories DROP CONSTRAINT chk_financial_categories_system_key;
ALTER TABLE financial_categories ADD CONSTRAINT chk_financial_categories_system_key
    CHECK (system_key IN ('PURCHASE_PAYMENT', 'PURCHASE_SHIPPING', 'CASH_SURPLUS', 'CASH_SHORTAGE'));

UPDATE financial_categories SET system_key = 'CASH_SURPLUS'
WHERE id = (SELECT id FROM financial_categories
            WHERE LOWER(name) = 'ajuste' AND type = 'INGRESO' AND deleted_at IS NULL
            ORDER BY created_at LIMIT 1);
INSERT INTO financial_categories (name, type, color, system_key)
SELECT 'Ajuste', 'INGRESO', '#6B7280', 'CASH_SURPLUS'
WHERE NOT EXISTS (SELECT 1 FROM financial_categories WHERE system_key = 'CASH_SURPLUS');

UPDATE financial_categories SET system_key = 'CASH_SHORTAGE'
WHERE id = (SELECT id FROM financial_categories
            WHERE LOWER(name) = 'ajuste' AND type = 'GASTO' AND deleted_at IS NULL
            ORDER BY created_at LIMIT 1);
INSERT INTO financial_categories (name, type, color, system_key)
SELECT 'Ajuste', 'GASTO', '#6B7280', 'CASH_SHORTAGE'
WHERE NOT EXISTS (SELECT 1 FROM financial_categories WHERE system_key = 'CASH_SHORTAGE');
