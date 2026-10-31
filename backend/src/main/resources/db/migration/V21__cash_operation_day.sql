-- CAJ-11: day of operation (store day, America/La_Paz) of a cash session and of everything that counts for a day.
-- Sessions, sales and movements keep their real date and time of record; reports group and filter by this day.
ALTER TABLE cash_sessions          ADD COLUMN operation_date DATE;
ALTER TABLE sales                  ADD COLUMN operation_date DATE;
ALTER TABLE financial_transactions ADD COLUMN operation_date DATE;
ALTER TABLE purchase_payments      ADD COLUMN operation_date DATE;
ALTER TABLE creditor_repayments    ADD COLUMN operation_date DATE;
ALTER TABLE customer_collections   ADD COLUMN operation_date DATE;

-- Nothing changes on deploy: each record keeps the store day it is counted on today (the same expression the
-- reports used). Historical records (FIN-11) are at 09:00 or 12:00 of the sheet day, so they keep that day.
UPDATE cash_sessions          SET operation_date = (opened_at        AT TIME ZONE 'America/La_Paz')::date;
UPDATE sales                  SET operation_date = (created_at       AT TIME ZONE 'America/La_Paz')::date;
UPDATE financial_transactions SET operation_date = (transaction_date AT TIME ZONE 'America/La_Paz')::date;
UPDATE purchase_payments      SET operation_date = (payment_date     AT TIME ZONE 'America/La_Paz')::date;
UPDATE creditor_repayments    SET operation_date = (repayment_date   AT TIME ZONE 'America/La_Paz')::date;
UPDATE customer_collections   SET operation_date = (collection_date  AT TIME ZONE 'America/La_Paz')::date;

ALTER TABLE cash_sessions          ALTER COLUMN operation_date SET NOT NULL;
ALTER TABLE sales                  ALTER COLUMN operation_date SET NOT NULL;
ALTER TABLE financial_transactions ALTER COLUMN operation_date SET NOT NULL;
ALTER TABLE purchase_payments      ALTER COLUMN operation_date SET NOT NULL;
ALTER TABLE creditor_repayments    ALTER COLUMN operation_date SET NOT NULL;
ALTER TABLE customer_collections   ALTER COLUMN operation_date SET NOT NULL;

CREATE INDEX idx_cash_sessions_operation_date ON cash_sessions(operation_date);
CREATE INDEX idx_sales_operation_date ON sales(operation_date DESC, created_at DESC);
CREATE INDEX idx_financial_tx_operation_date ON financial_transactions(operation_date DESC, transaction_date DESC);
