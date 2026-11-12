-- COM-02 / DEU-02: the shipping of a purchase paid by a person. No money left the business: it only raises the
-- debt with them, like a purchase payment made by a person. A change of the shipping voids it with a reason and
-- records another (COM-12), so the history of the person is never rewritten.

CREATE TABLE purchase_shipping_charges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_id UUID NOT NULL REFERENCES purchases(id),
    creditor_id UUID NOT NULL REFERENCES creditors(id),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    charge_date TIMESTAMP WITH TIME ZONE NOT NULL,
    operation_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (status IN ('ACTIVO', 'ANULADO')),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    voided_by VARCHAR(100),
    voided_at TIMESTAMP WITH TIME ZONE,
    void_reason VARCHAR(500)
);

-- A purchase has one active shipping at most
CREATE UNIQUE INDEX uq_purchase_shipping_charges_active ON purchase_shipping_charges(purchase_id)
    WHERE status = 'ACTIVO';
CREATE INDEX idx_purchase_shipping_charges_creditor ON purchase_shipping_charges(creditor_id);
