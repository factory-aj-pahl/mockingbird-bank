-- QA-only second account holder for the cross-account isolation flow (qa-web W11).
-- Idempotent. Run only AFTER the app has started once: DataInitializer seeds the demo holder
-- only when account_holder is empty. Remove with cleanup-other-holder.sql.
BEGIN;

INSERT INTO account_holder (full_name, email)
VALUES ('Casey Other (QA)', 'qa.other.holder@example.invalid')
ON CONFLICT (email) DO NOTHING;

INSERT INTO account (holder_id, account_number, account_type, nickname, balance, opened_at)
SELECT h.id, '9000000000017', 'CHECKING', 'QA Other Checking', 777.77, DATE '2024-01-02'
FROM account_holder h
WHERE h.email = 'qa.other.holder@example.invalid'
ON CONFLICT (account_number) DO NOTHING;

INSERT INTO transaction (account_id, posted_at, description, category, amount)
SELECT a.id, now(), 'QA-OTHER-HOLDER-MARKER', 'QA', -1.23
FROM account a
WHERE a.account_number = '9000000000017'
  AND NOT EXISTS (SELECT 1 FROM transaction t WHERE t.account_id = a.id);

COMMIT;

SELECT 'OTHER_ACCOUNT_ID=' || id AS other_account FROM account WHERE account_number = '9000000000017';
