-- DEU-03 (trabajo 062): the repayments recorded before V31 did not say what they paid. Each one covers the oldest
-- debts of its concept first (Pedido: purchase payments and shipping paid by the person; Pago a tercero: sales of
-- their products), by day of operation (CAJ-11), and whatever is left of it covers the oldest debts of any kind.
-- The debt does not change: it is still computed from the totals. Only people without any marked repayment are
-- touched, so running it again changes nothing. In production (12-11-2026) every repayment covered whole debts.

INSERT INTO creditor_repayment_items (repayment_id, purchase_payment_id, shipping_charge_id, sale_id, amount)
WITH people AS (
    SELECT c.id FROM creditors c
    WHERE NOT EXISTS (SELECT 1 FROM creditor_repayment_items ri
                      JOIN creditor_repayments r ON r.id = ri.repayment_id
                      WHERE r.creditor_id = c.id)
),
debts AS (
    SELECT p.creditor_id, CAST('PURCHASE' AS VARCHAR(20)) AS concept, p.id AS debt_id, p.id AS payment_id,
           CAST(NULL AS UUID) AS shipping_id, CAST(NULL AS UUID) AS sale_id, p.amount,
           p.operation_date AS day, p.payment_date AS at, p.created_at
    FROM purchase_payments p
    WHERE p.creditor_id IN (SELECT id FROM people) AND p.status = 'ACTIVO'
    UNION ALL
    SELECT sc.creditor_id, 'PURCHASE', sc.id, NULL, sc.id, NULL, sc.amount, sc.operation_date, sc.charge_date,
           sc.created_at
    FROM purchase_shipping_charges sc
    WHERE sc.creditor_id IN (SELECT id FROM people) AND sc.status = 'ACTIVO'
    UNION ALL
    SELECT i.creditor_id, 'THIRD_PARTY', s.id, NULL, NULL, s.id, SUM(i.subtotal), s.operation_date, s.created_at,
           s.created_at
    FROM sale_items i
    JOIN sales s ON s.id = i.sale_id
    WHERE i.creditor_id IN (SELECT id FROM people) AND s.status = 'COMPLETADA'
    GROUP BY i.creditor_id, s.id, s.operation_date, s.created_at
),
repayments AS (
    SELECT r.id, r.creditor_id, r.concept, r.amount, r.operation_date AS day, r.repayment_date AS at, r.created_at
    FROM creditor_repayments r
    WHERE r.creditor_id IN (SELECT id FROM people) AND r.status = 'ACTIVO'
),
-- Pass 1: debts and repayments of each concept laid on their own line from 0, oldest first; a repayment pays the
-- part of each debt that falls inside its stretch of the line
debt_lines AS (
    SELECT d.*, SUM(d.amount) OVER (PARTITION BY d.creditor_id, d.concept
        ORDER BY d.day, d.at, d.created_at, d.debt_id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS hi
    FROM debts d
),
repayment_lines AS (
    SELECT r.*, SUM(r.amount) OVER (PARTITION BY r.creditor_id, r.concept
        ORDER BY r.day, r.at, r.created_at, r.id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS hi
    FROM repayments r
),
by_concept AS (
    SELECT rl.id AS repayment_id, dl.creditor_id, dl.debt_id,
           LEAST(dl.hi, rl.hi) - GREATEST(dl.hi - dl.amount, rl.hi - rl.amount) AS amount
    FROM debt_lines dl
    JOIN repayment_lines rl ON rl.creditor_id = dl.creditor_id AND rl.concept = dl.concept
    WHERE LEAST(dl.hi, rl.hi) > GREATEST(dl.hi - dl.amount, rl.hi - rl.amount)
),
-- Pass 2: what is left of each repayment covers what is left of any debt, on one line per person, oldest first
debts_left AS (
    SELECT d.*, d.amount - COALESCE((SELECT SUM(b.amount) FROM by_concept b
                                     WHERE b.debt_id = d.debt_id AND b.creditor_id = d.creditor_id), 0) AS rest
    FROM debts d
),
repayments_left AS (
    SELECT r.*, r.amount - COALESCE((SELECT SUM(b.amount) FROM by_concept b WHERE b.repayment_id = r.id), 0) AS rest
    FROM repayments r
),
left_debt_lines AS (
    SELECT d.*, SUM(d.rest) OVER (PARTITION BY d.creditor_id
        ORDER BY d.day, d.at, d.created_at, d.debt_id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS hi
    FROM debts_left d
    WHERE d.rest > 0
),
left_repayment_lines AS (
    SELECT r.*, SUM(r.rest) OVER (PARTITION BY r.creditor_id
        ORDER BY r.day, r.at, r.created_at, r.id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS hi
    FROM repayments_left r
    WHERE r.rest > 0
),
leftover AS (
    SELECT rl.id AS repayment_id, dl.creditor_id, dl.debt_id,
           LEAST(dl.hi, rl.hi) - GREATEST(dl.hi - dl.rest, rl.hi - rl.rest) AS amount
    FROM left_debt_lines dl
    JOIN left_repayment_lines rl ON rl.creditor_id = dl.creditor_id
    WHERE LEAST(dl.hi, rl.hi) > GREATEST(dl.hi - dl.rest, rl.hi - rl.rest)
),
marked AS (
    SELECT repayment_id, creditor_id, debt_id, amount FROM by_concept
    UNION ALL
    SELECT repayment_id, creditor_id, debt_id, amount FROM leftover
)
SELECT m.repayment_id, d.payment_id, d.shipping_id, d.sale_id, m.amount
FROM marked m
JOIN debts d ON d.debt_id = m.debt_id AND d.creditor_id = m.creditor_id;
