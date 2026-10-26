-- FIN-01: initial business accounts Caja, Bisa and Efectivo.
-- Caja already exists (V1). Bisa and Efectivo start at 0.00; the balance at the cutover is entered
-- once from Finanzas -> Cuentas before the account has other movements.
-- Accounts that already exist with the same name are reused.

WITH bisa AS (
    INSERT INTO financial_accounts (name, color, is_qr_account)
    SELECT 'Bisa', '#0284c7',
           NOT EXISTS (SELECT 1 FROM financial_accounts WHERE is_qr_account = TRUE AND deleted_at IS NULL)
    WHERE NOT EXISTS (SELECT 1 FROM financial_accounts WHERE LOWER(name) = 'bisa')
    RETURNING id
)
INSERT INTO financial_transactions (type, source_account_id, amount, transaction_date, description, created_by)
SELECT 'SALDO_INICIAL', id, 0.00, CURRENT_TIMESTAMP, 'Saldo inicial de apertura', 'SYSTEM' FROM bisa;

WITH efectivo AS (
    INSERT INTO financial_accounts (name, color)
    SELECT 'Efectivo', '#d97706'
    WHERE NOT EXISTS (SELECT 1 FROM financial_accounts WHERE LOWER(name) = 'efectivo')
    RETURNING id
)
INSERT INTO financial_transactions (type, source_account_id, amount, transaction_date, description, created_by)
SELECT 'SALDO_INICIAL', id, 0.00, CURRENT_TIMESTAMP, 'Saldo inicial de apertura', 'SYSTEM' FROM efectivo;
