-- Every money movement has a positive amount. The only exception is the opening balance (SALDO_INICIAL), which is
-- 0.00 for an account opened empty (V1, V14 and FIN-01). NOT VALID: existing rows are not checked, only new ones
-- and the ones that change.
ALTER TABLE financial_transactions
    ADD CONSTRAINT chk_financial_transactions_amount_positive
    CHECK (amount > 0 OR (type = 'SALDO_INICIAL' AND amount >= 0)) NOT VALID;

-- The name of a deleted account can be used again (FIN-01): unique only among active accounts, ignoring case, like
-- suppliers. The service already rejects a duplicate active name ignoring case (existsByNameIgnoreCase...).
ALTER TABLE financial_accounts DROP CONSTRAINT financial_accounts_name_key;
CREATE UNIQUE INDEX uq_financial_accounts_name_active ON financial_accounts (LOWER(name)) WHERE deleted_at IS NULL;
