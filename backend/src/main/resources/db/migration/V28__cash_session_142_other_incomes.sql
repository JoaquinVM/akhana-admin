-- FIN-11: one-time correction of historical cash session #142 (23/10/2026).
-- Why: three incomes from Tia Neida were missing from the spreadsheet, so that money showed up in the count as part
-- of the Bs 780.40 surplus. Decided by Joaquín on 03/11/2026: they are part of that surplus.
--
-- Applies only if the session exists, is historical and closed, and it and its movements still have the imported
-- amounts; otherwise nothing changes (development and test databases).
-- The three incomes go in as historical movements of the day, without cash_session_id like the others of a historical
-- session. The surplus movement is voided and replaced by one of Bs 455.40, so the total income of the day stays the
-- same. Closing amount, opening amount, sales and account balances stay the same: historical movements do not count
-- for the balances, and the money was already in the counted cash.

CREATE TEMPORARY TABLE fin11_session_142 AS
SELECT s.id AS session_id, s.historical_import_id, s.operation_date,
       surplus.id AS surplus_id, surplus.source_account_id, surplus.category_id AS surplus_category_id,
       surplus.transaction_date, other_income.category_id AS other_income_category_id
FROM cash_sessions s
JOIN financial_transactions surplus
    ON surplus.historical_import_id = s.historical_import_id
   AND surplus.operation_date = s.operation_date
   AND surplus.type = 'INGRESO_MANUAL'
   AND surplus.status = 'ACTIVO'
   AND surplus.amount = 780.40
   AND surplus.description = 'Sobrante'
JOIN financial_transactions other_income
    ON other_income.historical_import_id = s.historical_import_id
   AND other_income.operation_date = s.operation_date
   AND other_income.type = 'INGRESO_MANUAL'
   AND other_income.status = 'ACTIVO'
   AND other_income.amount = 579.00
   AND other_income.description = 'Fit Mit Tia Neida'
WHERE s.session_number = 142
  AND s.status = 'CERRADA'
  AND s.operation_date = DATE '2026-10-23'
  AND s.expected_cash = 6857.50
  AND s.difference = 780.40
  AND s.historical_other_income = 579.00;

INSERT INTO financial_transactions (historical_import_id, type, source_account_id, category_id, amount,
                                    transaction_date, operation_date, description, created_by)
SELECT f.historical_import_id, 'INGRESO_MANUAL', f.source_account_id, f.other_income_category_id, t.amount,
       f.transaction_date, f.operation_date, t.description, 'SYSTEM'
FROM fin11_session_142 f
CROSS JOIN (VALUES (161.00, 'Tia Neida Vida Amazonica'),
                   (84.00, 'Tia Neida 6 brownies'),
                   (80.00, 'Tia Neida 2 aceites de coco')) AS t(amount, description);

UPDATE financial_transactions ft
SET status = 'ANULADO', voided_at = CURRENT_TIMESTAMP, voided_by = 'SYSTEM',
    void_reason = 'Bs 325.00 del sobrante eran ingresos de Tia Neida: se reemplaza por un sobrante de Bs 455.40.',
    updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
FROM fin11_session_142 f
WHERE ft.id = f.surplus_id;

INSERT INTO financial_transactions (historical_import_id, type, source_account_id, category_id, amount,
                                    transaction_date, operation_date, description, created_by)
SELECT f.historical_import_id, 'INGRESO_MANUAL', f.source_account_id, f.surplus_category_id, 455.40,
       f.transaction_date, f.operation_date, 'Sobrante', 'SYSTEM'
FROM fin11_session_142 f;

UPDATE cash_sessions s
SET historical_other_income = 904.00, expected_cash = 7182.50, difference = 455.40
FROM fin11_session_142 f
WHERE s.id = f.session_id;

DROP TABLE fin11_session_142;
