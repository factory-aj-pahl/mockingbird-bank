-- Removes everything seed-other-holder.sql created. Safe to run repeatedly.
BEGIN;

DELETE FROM transaction
WHERE account_id IN (
    SELECT a.id FROM account a
    JOIN account_holder h ON h.id = a.holder_id
    WHERE h.email = 'qa.other.holder@example.invalid'
);

DELETE FROM account
WHERE holder_id IN (SELECT id FROM account_holder WHERE email = 'qa.other.holder@example.invalid');

DELETE FROM app_user
WHERE holder_id IN (SELECT id FROM account_holder WHERE email = 'qa.other.holder@example.invalid');

DELETE FROM account_holder WHERE email = 'qa.other.holder@example.invalid';

COMMIT;
