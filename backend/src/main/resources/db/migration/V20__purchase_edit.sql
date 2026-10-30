-- COM-11: a line removed while editing a purchase (it did not arrive) stays, out of the totals
ALTER TABLE purchase_items
    ADD COLUMN removed_by VARCHAR(100),
    ADD COLUMN removed_at TIMESTAMP WITH TIME ZONE;

-- COM-12: who edited a purchase, when, why and what changed; never updated
CREATE TABLE purchase_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_id UUID NOT NULL REFERENCES purchases(id),
    reason VARCHAR(500) NOT NULL,
    changes TEXT NOT NULL,
    revised_by VARCHAR(100) NOT NULL,
    revised_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_purchase_revisions_purchase ON purchase_revisions(purchase_id);
