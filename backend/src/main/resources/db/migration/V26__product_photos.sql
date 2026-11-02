-- CAT-12: one active photo per product. Changing or removing a photo keeps its row and its files (RG-01)
CREATE TABLE product_photos (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    file_name VARCHAR(64) NOT NULL UNIQUE,
    thumbnail_file_name VARCHAR(64) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deactivated_by VARCHAR(100),
    deactivated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_product_photos_active CHECK (active = (deactivated_at IS NULL))
);

CREATE UNIQUE INDEX uq_product_photos_active ON product_photos(product_id) WHERE active;
