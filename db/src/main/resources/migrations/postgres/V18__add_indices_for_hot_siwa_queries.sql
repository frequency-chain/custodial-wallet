-- This should speed up provider application metadata lookups
CREATE INDEX CONCURRENTLY provider_application_provider_frequency_account_id_verified_credential_url
  ON custodial_wallet.provider_application(provider_frequency_account_id, verified_credential_url);

-- Add index for foreign key in `user_key_data`
CREATE INDEX CONCURRENTLY user_account_id
    on custodial_wallet.user_key_data(user_account_id);
