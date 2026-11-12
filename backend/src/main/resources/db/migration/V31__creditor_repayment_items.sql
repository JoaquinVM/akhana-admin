-- DEU-03 / DEU-04: what each repayment to a person paid. A repayment pays the debts the person had pending: purchase
-- payments and shipping paid by them (DEU-02) or sales of their products (POS-13, one row per sale). A debt is paid
-- while the rows of active repayments cover it; voiding the repayment leaves it pending again, so no row is deleted.

CREATE TABLE creditor_repayment_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repayment_id UUID NOT NULL REFERENCES creditor_repayments(id),
    purchase_payment_id UUID REFERENCES purchase_payments(id),
    shipping_charge_id UUID REFERENCES purchase_shipping_charges(id),
    sale_id UUID REFERENCES sales(id),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    CONSTRAINT chk_creditor_repayment_items_one_debt
        CHECK (num_nonnulls(purchase_payment_id, shipping_charge_id, sale_id) = 1)
);

CREATE INDEX idx_creditor_repayment_items_repayment ON creditor_repayment_items(repayment_id);
CREATE INDEX idx_creditor_repayment_items_payment ON creditor_repayment_items(purchase_payment_id);
CREATE INDEX idx_creditor_repayment_items_shipping ON creditor_repayment_items(shipping_charge_id);
CREATE INDEX idx_creditor_repayment_items_sale ON creditor_repayment_items(sale_id);
