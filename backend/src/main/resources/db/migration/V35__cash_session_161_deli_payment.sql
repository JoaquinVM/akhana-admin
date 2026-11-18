-- CAJ-06: one-time correction of closed cash session #161 (16/11/2026).
-- Why: the Bs 360 payment of purchase PED-000053 (Deli) left the drawer of #161, but it was recorded in #162 after
-- #161 had closed and was voided there ("CARGAR EN LA CAJA ANTERIOR 16/11/26"). A closed session takes no movements and
-- #161 can no longer be reopened (CAJ-14), so #161 closed with a Bs 260 shortage and the purchase shows as unpaid.
-- Decided by Joaquín on 18/11/2026: the whole Bs 360 came from the #161 drawer, and the Bs 100 surplus it leaves is right.
--
-- Applies only if #161 is closed, not historical, of 16/11 and still has the amounts below and its active Bs 260
-- shortage, PED-000053 is registered for Bs 360 with nothing paid, and its voided Caja payment is in #162; otherwise
-- nothing changes (development and test databases, or a second run).
-- The payment goes into #161 the way the app records one from Caja (a Pedido expense and its purchase payment), at the
-- time of the voided payment and on the day of #161. The shortage is voided with a reason and replaced by a Bs 100
-- surplus. #161 ends with expected cash 4133.20 and difference 100.00. The Caja balance does not change
-- (-360 + 260 + 100), nor do the counted amount, cuts, sales, session #162 or its voided payment.

CREATE TEMPORARY TABLE caj06_session_161 AS
SELECT s.id AS session_id, s.operation_date,
       shortage.id AS shortage_id, shortage.source_account_id AS cash_account_id,
       shortage.transaction_date AS closing_date,
       surplus_category.id AS surplus_category_id,
       p.id AS purchase_id,
       voided.category_id AS payment_category_id, voided.transaction_date AS payment_date
FROM cash_sessions s
JOIN financial_transactions shortage
    ON shortage.cash_session_id = s.id
   AND shortage.type = 'GASTO_MANUAL'
   AND shortage.status = 'ACTIVO'
   AND shortage.amount = 260.00
   AND shortage.description = 'Faltante al cierre de caja #161'
JOIN financial_categories shortage_category
    ON shortage_category.id = shortage.category_id
   AND shortage_category.system_key = 'CASH_SHORTAGE'
JOIN financial_accounts cash_account
    ON cash_account.id = shortage.source_account_id
   AND cash_account.is_cash_account = TRUE
JOIN financial_categories surplus_category
    ON surplus_category.system_key = 'CASH_SURPLUS'
   AND surplus_category.deleted_at IS NULL
JOIN purchases p
    ON p.purchase_number = 'PED-000053'
   AND p.status = 'REGISTRADO'
   AND p.total_amount = 360.00
   AND p.paid_amount = 0.00
JOIN financial_transactions voided
    ON voided.purchase_id = p.id
   AND voided.type = 'GASTO_MANUAL'
   AND voided.status = 'ANULADO'
   AND voided.amount = 360.00
   AND voided.source_account_id = cash_account.id
JOIN financial_categories payment_category
    ON payment_category.id = voided.category_id
   AND payment_category.system_key = 'PURCHASE_PAYMENT'
JOIN cash_sessions s162
    ON s162.id = voided.cash_session_id
   AND s162.session_number = 162
WHERE s.session_number = 161
  AND s.historical_import_id IS NULL
  AND s.status = 'CERRADA'
  AND s.operation_date = DATE '2026-11-16'
  AND s.opening_amount = 4067.70
  AND s.total_sales_cash = 2389.00
  AND s.expected_cash = 4493.20
  AND s.closing_amount = 4233.20
  AND s.difference = -260.00
  AND NOT EXISTS (SELECT 1 FROM purchase_payments pp WHERE pp.purchase_id = p.id AND pp.status = 'ACTIVO');

-- Exactly one match or nothing at all
DELETE FROM caj06_session_161 WHERE (SELECT count(*) FROM caj06_session_161) <> 1;

WITH payment_movement AS (
    INSERT INTO financial_transactions (type, source_account_id, category_id, amount, transaction_date, operation_date,
                                        description, notes, purchase_id, cash_session_id, created_by)
    SELECT 'GASTO_MANUAL', f.cash_account_id, f.payment_category_id, 360.00, f.payment_date, f.operation_date,
           'Pago pedido PED-000053',
           'Salió de la caja #161: se cargó en la #162 y se anuló ahí. Corrección decidida por Joaquín el 18/11/2026.',
           f.purchase_id, f.session_id, 'SYSTEM'
    FROM caj06_session_161 f
    RETURNING id, purchase_id, source_account_id, amount, transaction_date, operation_date
)
INSERT INTO purchase_payments (purchase_id, account_id, amount, payment_date, operation_date, financial_transaction_id,
                               created_by)
SELECT m.purchase_id, m.source_account_id, m.amount, m.transaction_date, m.operation_date, m.id, 'SYSTEM'
FROM payment_movement m;

UPDATE purchases p
SET paid_amount = p.paid_amount + 360.00, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
FROM caj06_session_161 f
WHERE p.id = f.purchase_id;

UPDATE financial_transactions ft
SET status = 'ANULADO', voided_at = CURRENT_TIMESTAMP, voided_by = 'SYSTEM',
    void_reason = 'El faltante era el pago de PED-000053 (Bs 360), que salió de esta caja: se reemplaza por un sobrante de Bs 100.',
    updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
FROM caj06_session_161 f
WHERE ft.id = f.shortage_id;

INSERT INTO financial_transactions (type, source_account_id, category_id, amount, transaction_date, operation_date,
                                    description, notes, cash_session_id, created_by)
SELECT 'INGRESO_MANUAL', f.cash_account_id, f.surplus_category_id, 100.00, f.closing_date, f.operation_date,
       'Sobrante al cierre de caja #161',
       'Reemplaza el faltante de Bs 260 al cargar en esta caja el pago de PED-000053. Corrección del 18/11/2026.',
       f.session_id, 'SYSTEM'
FROM caj06_session_161 f;

UPDATE cash_sessions s
SET expected_cash = 4133.20, difference = 100.00
FROM caj06_session_161 f
WHERE s.id = f.session_id;

DROP TABLE caj06_session_161;
