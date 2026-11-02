-- CAT-10: factory barcodes of a product. Removing one keeps its row (RG-01); EAN-8 keeps 8 digits and UPC-A is stored
-- as 13, with a leading 0
CREATE TABLE product_barcodes (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    barcode VARCHAR(13) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deactivated_by VARCHAR(100),
    deactivated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_product_barcodes_format CHECK (barcode ~ '^([0-9]{8}|[0-9]{13})$'),
    CONSTRAINT chk_product_barcodes_active CHECK (active = (deactivated_at IS NULL))
);

CREATE UNIQUE INDEX uq_product_barcodes_active ON product_barcodes(barcode) WHERE active;
CREATE INDEX idx_product_barcodes_product ON product_barcodes(product_id);

-- CAT-10: «No trae»: the product was checked and has no factory barcode
ALTER TABLE products ADD COLUMN no_barcode_marked_by VARCHAR(100);
ALTER TABLE products ADD COLUMN no_barcode_marked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE products ADD CONSTRAINT chk_products_no_barcode
    CHECK ((no_barcode_marked_by IS NULL) = (no_barcode_marked_at IS NULL));

-- POS-15: whether the line came in by a scan, only to measure the use of the reader
ALTER TABLE sale_items ADD COLUMN scanned BOOLEAN NOT NULL DEFAULT FALSE;
