-- DEU-01 to DEU-04: people the business owes money to (creditors), purchase payments made by them and
-- repayments to them. Debts are not stored: they are computed from active payments and repayments.

CREATE TABLE creditors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (status IN ('ACTIVO', 'INACTIVO', 'ELIMINADO')),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Name unique without case among active and inactive people (DEU-01)
CREATE UNIQUE INDEX uq_creditors_name ON creditors (LOWER(name)) WHERE status <> 'ELIMINADO';

-- -----------------------------------------------------------------------------
-- Purchase payments made by a person (DEU-02): exactly one source, account or person.
-- Only account payments have a financial movement.
-- -----------------------------------------------------------------------------
ALTER TABLE purchase_payments ADD COLUMN creditor_id UUID REFERENCES creditors(id);
ALTER TABLE purchase_payments ALTER COLUMN account_id DROP NOT NULL;
ALTER TABLE purchase_payments ALTER COLUMN financial_transaction_id DROP NOT NULL;
ALTER TABLE purchase_payments ADD CONSTRAINT chk_purchase_payments_one_source
    CHECK ((account_id IS NULL) <> (creditor_id IS NULL));
ALTER TABLE purchase_payments ADD CONSTRAINT chk_purchase_payments_transaction
    CHECK ((account_id IS NULL) = (financial_transaction_id IS NULL));

CREATE INDEX idx_purchase_payments_creditor ON purchase_payments(creditor_id) WHERE creditor_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- Repayments to a person (DEU-03). Each one generates a Pedido expense (P-34) that points to it;
-- the link lives only in financial_transactions to avoid a circular reference.
-- -----------------------------------------------------------------------------
CREATE TABLE creditor_repayments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creditor_id UUID NOT NULL REFERENCES creditors(id),
    account_id UUID NOT NULL REFERENCES financial_accounts(id),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    repayment_date TIMESTAMP WITH TIME ZONE NOT NULL,
    comment VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (status IN ('ACTIVO', 'ANULADO')),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    voided_by VARCHAR(100),
    voided_at TIMESTAMP WITH TIME ZONE,
    void_reason VARCHAR(500)
);

CREATE INDEX idx_creditor_repayments_creditor ON creditor_repayments(creditor_id, repayment_date);

ALTER TABLE financial_transactions ADD COLUMN creditor_repayment_id UUID REFERENCES creditor_repayments(id);
CREATE UNIQUE INDEX uq_financial_tx_creditor_repayment ON financial_transactions(creditor_repayment_id)
    WHERE creditor_repayment_id IS NOT NULL;
