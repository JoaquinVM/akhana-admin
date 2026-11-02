-- CAJ-12: one cash session per day of operation, historical sessions included. A production database has no repeated
-- days and gets the full index. A development database may have them: nothing is moved, and the index leaves out the
-- sessions up to the last repeated number. New sessions are then protected among themselves by the index, and against
-- the older ones by the rule of the service (a new day must be after the day of the last session).
DO $$
DECLARE
    last_repeated BIGINT;
BEGIN
    SELECT MAX(session_number) INTO last_repeated
    FROM cash_sessions
    WHERE operation_date IN (SELECT operation_date FROM cash_sessions GROUP BY operation_date HAVING COUNT(*) > 1);

    IF last_repeated IS NULL THEN
        CREATE UNIQUE INDEX uq_cash_sessions_operation_date ON cash_sessions (operation_date);
    ELSE
        RAISE WARNING 'cash_sessions has repeated days up to #%: partial day index, no data moved', last_repeated;
        EXECUTE format('CREATE UNIQUE INDEX uq_cash_sessions_operation_date ON cash_sessions (operation_date) '
                       'WHERE session_number > %s', last_repeated);
    END IF;
END $$;
