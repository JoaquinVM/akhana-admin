-- FIN-11: one-time historical migration of the two spreadsheets. Every imported record points to the import
-- (who and when); a record without it was registered in the system. Historical records feed the reports but
-- not the account balances, the cash opening proposal or the cash sessions of the system.

CREATE TABLE historical_imports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cutoff_date DATE NOT NULL,
    imported_by VARCHAR(100) NOT NULL,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- Sales: one per day and type, without cash session or payments (P-32), with their own number series
-- -----------------------------------------------------------------------------
ALTER TABLE sales ADD COLUMN historical_import_id UUID REFERENCES historical_imports(id);
ALTER TABLE sales ALTER COLUMN cash_session_id DROP NOT NULL;
ALTER TABLE sales ADD CONSTRAINT chk_sales_cash_session
    CHECK (cash_session_id IS NOT NULL OR historical_import_id IS NOT NULL);

ALTER TABLE sales DROP CONSTRAINT sales_payment_method_check;
ALTER TABLE sales ADD CONSTRAINT sales_payment_method_check
    CHECK (payment_method IN ('EFECTIVO', 'QR', 'MIXTO', 'CREDITO', 'HISTORICO'));
ALTER TABLE sales ADD CONSTRAINT chk_sales_historical_payment
    CHECK ((payment_method = 'HISTORICO') = (historical_import_id IS NOT NULL));

CREATE SEQUENCE historical_sale_number_seq;

-- -----------------------------------------------------------------------------
-- Movements of Transacc.: never part of a cash session nor of the balances (FIN-08)
-- -----------------------------------------------------------------------------
ALTER TABLE financial_transactions ADD COLUMN historical_import_id UUID REFERENCES historical_imports(id);
CREATE INDEX idx_financial_tx_historical ON financial_transactions(historical_import_id)
    WHERE historical_import_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- Closed cash sessions of Cierre de Caja, with the amounts of the spreadsheet that the system otherwise
-- computes from movements
-- -----------------------------------------------------------------------------
ALTER TABLE cash_sessions
    ADD COLUMN historical_import_id UUID REFERENCES historical_imports(id),
    ADD COLUMN historical_other_income NUMERIC(12, 2),
    ADD COLUMN historical_expenses NUMERIC(12, 2),
    ADD COLUMN historical_other_outflow NUMERIC(12, 2),
    ADD COLUMN historical_internal_outflow NUMERIC(12, 2);
ALTER TABLE cash_sessions ADD CONSTRAINT chk_cash_sessions_historical
    CHECK (historical_import_id IS NULL OR status = 'CERRADA');

-- -----------------------------------------------------------------------------
-- Purchases of Akhana Pedidos: the ones before order 342 have no lines; their payments move no money (P-43)
-- -----------------------------------------------------------------------------
ALTER TABLE purchases ADD COLUMN historical_import_id UUID REFERENCES historical_imports(id);
ALTER TABLE purchase_payments ADD COLUMN historical_import_id UUID REFERENCES historical_imports(id);

ALTER TABLE purchase_payments DROP CONSTRAINT chk_purchase_payments_one_source;
ALTER TABLE purchase_payments ADD CONSTRAINT chk_purchase_payments_one_source
    CHECK (NOT (account_id IS NOT NULL AND creditor_id IS NOT NULL)
           AND (historical_import_id IS NOT NULL OR (account_id IS NULL) <> (creditor_id IS NULL)));
ALTER TABLE purchase_payments DROP CONSTRAINT chk_purchase_payments_transaction;
ALTER TABLE purchase_payments ADD CONSTRAINT chk_purchase_payments_transaction
    CHECK (CASE WHEN historical_import_id IS NOT NULL THEN financial_transaction_id IS NULL
                ELSE (account_id IS NULL) = (financial_transaction_id IS NULL) END);

-- -----------------------------------------------------------------------------
-- Consumptions of the Consumos sheet and the gifts and breakages of Ventas (P-39)
-- -----------------------------------------------------------------------------
ALTER TABLE consumptions ADD COLUMN historical_import_id UUID REFERENCES historical_imports(id);
