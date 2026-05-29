-- Removing the unique constraint on wallet id allows a one to many relationship between a wallet and credentials
ALTER TABLE credential DROP CONSTRAINT credential_wallet_id_key;