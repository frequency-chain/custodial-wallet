-- We arrived at the decision to limit user_identifier's from referencing multiple accounts in order to simplify
-- migration/login application logic, but are using a constraint so we can easily reverse this decision later.
ALTER TABLE user_account_user_identifier
ADD UNIQUE (user_identifier_id);
