-- CAJ-04: two loose amounts of the count, the bills and the coins kept in reserve, that add up to the counted
-- amount with the cuts. Optional: the closings before this one have none.
ALTER TABLE cash_sessions
    ADD COLUMN closing_reserve_bills NUMERIC(12, 2) CHECK (closing_reserve_bills >= 0),
    ADD COLUMN closing_reserve_coins NUMERIC(12, 2) CHECK (closing_reserve_coins >= 0);

-- CAJ-14: a reopening keeps the reserves of the closing it undid
ALTER TABLE cash_session_reopenings
    ADD COLUMN closing_reserve_bills NUMERIC(12, 2),
    ADD COLUMN closing_reserve_coins NUMERIC(12, 2);
