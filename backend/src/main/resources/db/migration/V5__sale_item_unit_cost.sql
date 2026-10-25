-- T-07 (POS-05): each sale line keeps the product cost at the moment of the sale,
-- so a later cost change (CAT-07, COM-03) does not alter historical profit.

ALTER TABLE sale_items ADD COLUMN unit_cost NUMERIC(12, 2);

-- There is no production data yet (T-01): existing development lines take the current product cost.
-- Historical sales imported later (FIN-11) bring their own cost per line.
UPDATE sale_items si
SET unit_cost = p.buy_price
FROM products p
WHERE p.id = si.product_id;

ALTER TABLE sale_items ALTER COLUMN unit_cost SET NOT NULL;
ALTER TABLE sale_items ADD CONSTRAINT chk_sale_items_unit_cost CHECK (unit_cost >= 0);
