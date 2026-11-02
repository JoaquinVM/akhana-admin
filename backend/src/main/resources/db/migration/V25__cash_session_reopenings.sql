-- CAJ-14: one row per reopening, never updated, with what the undone closing had
CREATE TABLE cash_session_reopenings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cash_session_id UUID NOT NULL REFERENCES cash_sessions(id),
    reason VARCHAR(500) NOT NULL,
    reopened_by VARCHAR(100) NOT NULL,
    reopened_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closing_amount NUMERIC(12, 2) NOT NULL,
    closing_comment VARCHAR(500),
    closed_by VARCHAR(100),
    closed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expected_cash NUMERIC(12, 2) NOT NULL,
    difference NUMERIC(12, 2),
    voided_adjustment_id UUID REFERENCES financial_transactions(id)
);
CREATE INDEX idx_cash_session_reopenings_session ON cash_session_reopenings(cash_session_id, reopened_at);

-- The cuts of an undone closing stay where they are, tied to their reopening; nothing is deleted
ALTER TABLE cash_denomination_cuts ADD COLUMN reopening_id UUID REFERENCES cash_session_reopenings(id);
CREATE INDEX idx_cash_cuts_reopening ON cash_denomination_cuts(reopening_id);
