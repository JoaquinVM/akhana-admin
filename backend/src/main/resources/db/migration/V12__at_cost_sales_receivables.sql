-- CON-02 and DEU-05 to DEU-07: at-cost sales, customers that can buy at cost on credit and collections from them.
-- Receivables are not stored: they are computed from the credit of active sales minus active collections.

-- -----------------------------------------------------------------------------
-- Sale type (CON-02): a whole sale is NORMAL or AL_COSTO (price = cost, no profit)
-- -----------------------------------------------------------------------------
ALTER TABLE sales ADD COLUMN sale_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
    CHECK (sale_type IN ('NORMAL', 'AL_COSTO'));
CREATE INDEX idx_sales_sale_type ON sales(sale_type);

-- -----------------------------------------------------------------------------
-- Customers (DEU-05): same rules as creditors (unique name without case, logical delete)
-- -----------------------------------------------------------------------------
CREATE TABLE customers (
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

CREATE UNIQUE INDEX uq_customers_name ON customers (LOWER(name)) WHERE status <> 'ELIMINADO';

-- -----------------------------------------------------------------------------
-- Credit on a sale (DEU-05): only at-cost sales, with a customer; paid now + credit = total.
-- A sale fully on credit has payment method CREDITO.
-- -----------------------------------------------------------------------------
ALTER TABLE sales ADD COLUMN customer_id UUID REFERENCES customers(id);
ALTER TABLE sales ADD COLUMN credit_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE sales ADD CONSTRAINT chk_sales_credit_amount
    CHECK (credit_amount >= 0 AND credit_amount <= total_amount);
ALTER TABLE sales ADD CONSTRAINT chk_sales_credit_at_cost
    CHECK (credit_amount = 0 OR (sale_type = 'AL_COSTO' AND customer_id IS NOT NULL));

ALTER TABLE sales DROP CONSTRAINT sales_payment_method_check;
ALTER TABLE sales ADD CONSTRAINT sales_payment_method_check
    CHECK (payment_method IN ('EFECTIVO', 'QR', 'MIXTO', 'CREDITO'));

CREATE INDEX idx_sales_customer ON sales(customer_id) WHERE customer_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- Collections (DEU-06): into Caja (open session) or the QR account. Each one generates a Venta income (P-34)
-- that points to it; the link lives only in financial_transactions, like creditor repayments.
-- -----------------------------------------------------------------------------
CREATE TABLE customer_collections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customers(id),
    account_id UUID NOT NULL REFERENCES financial_accounts(id),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    collection_date TIMESTAMP WITH TIME ZONE NOT NULL,
    comment VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (status IN ('ACTIVO', 'ANULADO')),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    voided_by VARCHAR(100),
    voided_at TIMESTAMP WITH TIME ZONE,
    void_reason VARCHAR(500)
);

CREATE INDEX idx_customer_collections_customer ON customer_collections(customer_id, collection_date);

ALTER TABLE financial_transactions ADD COLUMN customer_collection_id UUID REFERENCES customer_collections(id);
CREATE UNIQUE INDEX uq_financial_tx_customer_collection ON financial_transactions(customer_collection_id)
    WHERE customer_collection_id IS NOT NULL;
