-- CAT-13: products marked for the public catalog; every change keeps who and when
ALTER TABLE products ADD COLUMN web_published BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE product_web_publication_changes (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    published BOOLEAN NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_product_web_publication_changes_product ON product_web_publication_changes(product_id);
