-- COM-07: delivery type, status and date of a purchase. While pending the date is the expected one;
-- once delivered it is the delivery date.
ALTER TABLE purchases ADD COLUMN delivery_type VARCHAR(20) NOT NULL DEFAULT 'EN_TIENDA'
    CHECK (delivery_type IN ('EN_PUERTA', 'TERMINAL', 'EN_TIENDA'));
ALTER TABLE purchases ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
    CHECK (delivery_status IN ('PENDIENTE', 'ENTREGADO'));
ALTER TABLE purchases ADD COLUMN delivery_date DATE;
ALTER TABLE purchases ADD CONSTRAINT chk_purchases_delivered_date
    CHECK (delivery_status <> 'ENTREGADO' OR delivery_date IS NOT NULL);

-- Payables view (COM-08): registered purchases with a balance
CREATE INDEX idx_purchases_payable ON purchases(supplier_id, purchase_date)
    WHERE status = 'REGISTRADO' AND paid_amount < items_total;
