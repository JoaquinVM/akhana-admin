-- CAJ-13: days confirmed without attention when opening a later session. A Sunday is never asked about or marked.
CREATE TABLE cash_no_attention_days (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    no_attention_date DATE NOT NULL UNIQUE CHECK (EXTRACT(ISODOW FROM no_attention_date) <> 7),
    comment VARCHAR(500) NOT NULL,
    -- The opening that confirmed them
    cash_session_id UUID NOT NULL REFERENCES cash_sessions(id),
    marked_by VARCHAR(100) NOT NULL,
    marked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
