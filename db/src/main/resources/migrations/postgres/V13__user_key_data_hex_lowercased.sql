CREATE INDEX CONCURRENTLY user_key_data_hex_lowercased ON custodial_wallet.user_key_data(lower(public_key_hex));
