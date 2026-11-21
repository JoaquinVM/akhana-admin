-- 097: a cash payment is rounded to Bs 0.10; the rounding (between -0.05 and +0.05) is kept on the sale and is part
-- of its total. Sales recorded before it have no rounding
ALTER TABLE sales ADD COLUMN cash_rounding NUMERIC(12, 2) NOT NULL DEFAULT 0.00;
