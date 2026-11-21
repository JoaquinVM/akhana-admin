-- POS-18, COM-27: the id the screen makes for each new sale or purchase and keeps on its retries, so a lost answer
-- never records it twice. Null for old, migrated and old-client records, so the index is only over the filled ones.
ALTER TABLE sales ADD COLUMN client_request_id UUID;
CREATE UNIQUE INDEX ux_sales_client_request_id ON sales (client_request_id) WHERE client_request_id IS NOT NULL;

ALTER TABLE purchases ADD COLUMN client_request_id UUID;
CREATE UNIQUE INDEX ux_purchases_client_request_id ON purchases (client_request_id)
    WHERE client_request_id IS NOT NULL;
