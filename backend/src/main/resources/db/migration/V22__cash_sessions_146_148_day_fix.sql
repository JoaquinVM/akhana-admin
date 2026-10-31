-- CAJ-11: one-time correction of the day of operation of cash sessions #146, #147 and #148.
-- Why: the sales of 28/10, 29/10 and 30/10/2026 were recorded in sessions opened on other days (#146, #147, and #148
-- opened on 31/10 to load 30/10), so those days showed zero in Caja, the reports and the dashboard.
-- Decided by Joaquín on 31/10/2026: #146 -> 2026-10-28, #147 -> 2026-10-29, #148 -> 2026-10-30.
--
-- Each session moves only if it exists, is not historical (FIN-11), is closed and was opened (America/La_Paz)
-- between 2026-10-28 and 2026-11-02; otherwise it is left as it is (development and test databases).
-- With each session move: its sales (voided too), its movements (by cash_session_id, voided and adjustments too),
-- the movements of those sales (the QR income has no cash_session_id) and the purchase payments, repayments and
-- collections of those movements. Only operation_date changes: amounts, expected cash, closing amount, difference,
-- cuts, numbers, users, real dates and times and balances stay the same.

CREATE TEMPORARY TABLE caj11_day_fix AS
SELECT s.id AS session_id, t.operation_date
FROM (VALUES (146, DATE '2026-10-28'), (147, DATE '2026-10-29'), (148, DATE '2026-10-30')) AS t(session_number, operation_date)
JOIN cash_sessions s ON s.session_number = t.session_number
WHERE s.historical_import_id IS NULL
  AND s.status = 'CERRADA'
  AND (s.opened_at AT TIME ZONE 'America/La_Paz')::date BETWEEN DATE '2026-10-28' AND DATE '2026-11-02';

CREATE TEMPORARY TABLE caj11_day_fix_movements AS
SELECT ft.id AS movement_id, f.operation_date
FROM financial_transactions ft
JOIN caj11_day_fix f ON ft.cash_session_id = f.session_id
UNION
SELECT ft.id, f.operation_date
FROM financial_transactions ft
JOIN sales sa ON sa.id = ft.sale_id
JOIN caj11_day_fix f ON sa.cash_session_id = f.session_id;

UPDATE cash_sessions s SET operation_date = f.operation_date
FROM caj11_day_fix f WHERE s.id = f.session_id;

UPDATE sales sa SET operation_date = f.operation_date
FROM caj11_day_fix f WHERE sa.cash_session_id = f.session_id;

UPDATE financial_transactions ft SET operation_date = m.operation_date
FROM caj11_day_fix_movements m WHERE ft.id = m.movement_id;

UPDATE purchase_payments pp SET operation_date = m.operation_date
FROM caj11_day_fix_movements m WHERE pp.financial_transaction_id = m.movement_id;

UPDATE creditor_repayments cr SET operation_date = m.operation_date
FROM caj11_day_fix_movements m
JOIN financial_transactions ft ON ft.id = m.movement_id
WHERE cr.id = ft.creditor_repayment_id;

UPDATE customer_collections cc SET operation_date = m.operation_date
FROM caj11_day_fix_movements m
JOIN financial_transactions ft ON ft.id = m.movement_id
WHERE cc.id = ft.customer_collection_id;

DROP TABLE caj11_day_fix_movements;
DROP TABLE caj11_day_fix;
