-- COM-22: a purchase from a text keeps its cost to confirm until the real prices are registered
ALTER TABLE purchases ADD COLUMN cost_to_confirm BOOLEAN NOT NULL DEFAULT FALSE;

-- COM-24, COM-25: what the AI read, kept with its purchase, and what each call cost
CREATE TABLE purchase_readings (
    id UUID PRIMARY KEY,
    source VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL,
    model VARCHAR(50) NOT NULL,
    result TEXT,
    input_tokens BIGINT,
    output_tokens BIGINT,
    cost_usd NUMERIC(10, 6) NOT NULL,
    purchase_id UUID REFERENCES purchases(id),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_purchase_readings_source CHECK (source IN ('TEXT', 'PHOTO')),
    CONSTRAINT chk_purchase_readings_status CHECK (status IN ('READ', 'FAILED'))
);

CREATE INDEX idx_purchase_readings_created_at ON purchase_readings(created_at);
CREATE INDEX idx_purchase_readings_purchase ON purchase_readings(purchase_id);

-- COM-23: one active invoice photo per purchase. A new one keeps the old row and its files (RG-01)
CREATE TABLE purchase_photos (
    id UUID PRIMARY KEY,
    purchase_id UUID NOT NULL REFERENCES purchases(id),
    file_name VARCHAR(64) NOT NULL UNIQUE,
    thumbnail_file_name VARCHAR(64) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deactivated_by VARCHAR(100),
    deactivated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_purchase_photos_active CHECK (active = (deactivated_at IS NULL))
);

CREATE UNIQUE INDEX uq_purchase_photos_active ON purchase_photos(purchase_id) WHERE active;
