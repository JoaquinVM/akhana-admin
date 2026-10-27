-- POS-11 to POS-13: special products sold with a name and price typed in the POS (free lines), the unidentified
-- product whose lines are assigned later and third-party products whose sales are owed to their owner.

-- -----------------------------------------------------------------------------
-- Special products (POS-11): no cost or price in the catalog; a third-party one has its owner (DEU-01)
-- -----------------------------------------------------------------------------
ALTER TABLE products ADD COLUMN special_type VARCHAR(20)
    CHECK (special_type IN ('UNIDENTIFIED', 'THIRD_PARTY'));
ALTER TABLE products ADD COLUMN owner_creditor_id UUID REFERENCES creditors(id);
ALTER TABLE products ADD CONSTRAINT chk_products_third_party_owner
    CHECK ((special_type = 'THIRD_PARTY') = (owner_creditor_id IS NOT NULL));

-- Only one unidentified product that is not eliminated
CREATE UNIQUE INDEX uq_products_unidentified ON products (special_type)
    WHERE special_type = 'UNIDENTIFIED' AND status <> 'ELIMINADO';
CREATE INDEX idx_products_owner_creditor ON products(owner_creditor_id) WHERE owner_creditor_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- Free lines (POS-11): the typed name stays in free_name even after the line is assigned (POS-12).
-- Unidentified lines are assigned once; third-party lines keep a copy of the owner (POS-13)
-- -----------------------------------------------------------------------------
ALTER TABLE sale_items ADD COLUMN free_line_type VARCHAR(20)
    CHECK (free_line_type IN ('UNIDENTIFIED', 'THIRD_PARTY'));
ALTER TABLE sale_items ADD COLUMN free_name VARCHAR(150);
ALTER TABLE sale_items ADD COLUMN assignment_status VARCHAR(20)
    CHECK (assignment_status IN ('POR_ASIGNAR', 'ASIGNADO', 'SIN_IDENTIFICAR'));
ALTER TABLE sale_items ADD COLUMN assigned_by VARCHAR(100);
ALTER TABLE sale_items ADD COLUMN assigned_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE sale_items ADD COLUMN creditor_id UUID REFERENCES creditors(id);

ALTER TABLE sale_items ADD CONSTRAINT chk_sale_items_free_name
    CHECK ((free_line_type IS NULL) = (free_name IS NULL));
ALTER TABLE sale_items ADD CONSTRAINT chk_sale_items_assignment
    CHECK ((free_line_type = 'UNIDENTIFIED') = (assignment_status IS NOT NULL));
-- Who and when only once the line was assigned or closed
ALTER TABLE sale_items ADD CONSTRAINT chk_sale_items_assigned_audit
    CHECK ((COALESCE(assignment_status, 'POR_ASIGNAR') = 'POR_ASIGNAR') = (assigned_at IS NULL AND assigned_by IS NULL));
ALTER TABLE sale_items ADD CONSTRAINT chk_sale_items_third_party_owner
    CHECK ((free_line_type = 'THIRD_PARTY') = (creditor_id IS NOT NULL));

CREATE INDEX idx_sale_items_pending ON sale_items(sale_id) WHERE assignment_status = 'POR_ASIGNAR';
CREATE INDEX idx_sale_items_creditor ON sale_items(creditor_id) WHERE creditor_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- Pago a tercero (FIN-05): repayments of third-party sales to their owner (POS-13)
-- -----------------------------------------------------------------------------
ALTER TABLE financial_categories DROP CONSTRAINT chk_financial_categories_system_key;
ALTER TABLE financial_categories ADD CONSTRAINT chk_financial_categories_system_key
    CHECK (system_key IN ('PURCHASE_PAYMENT', 'PURCHASE_SHIPPING', 'CASH_SURPLUS', 'CASH_SHORTAGE',
                          'THIRD_PARTY_PAYMENT'));

UPDATE financial_categories SET system_key = 'THIRD_PARTY_PAYMENT'
WHERE id = (SELECT id FROM financial_categories
            WHERE LOWER(name) = 'pago a tercero' AND type = 'GASTO' AND deleted_at IS NULL AND system_key IS NULL
            ORDER BY created_at LIMIT 1);
INSERT INTO financial_categories (name, type, color, system_key)
SELECT 'Pago a tercero', 'GASTO', '#9333EA', 'THIRD_PARTY_PAYMENT'
WHERE NOT EXISTS (SELECT 1 FROM financial_categories WHERE system_key = 'THIRD_PARTY_PAYMENT');

-- What a repayment pays back: purchases paid by the person (Pedido, P-34) or third-party sales (Pago a tercero)
ALTER TABLE creditor_repayments ADD COLUMN concept VARCHAR(20) NOT NULL DEFAULT 'PURCHASE'
    CHECK (concept IN ('PURCHASE', 'THIRD_PARTY'));
