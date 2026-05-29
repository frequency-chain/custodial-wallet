CREATE TABLE user_seed_data
(
    id                       BIGSERIAL PRIMARY KEY,
    user_account_id          BIGINT       NOT NULL REFERENCES user_account,
    seed_usage_type          VARCHAR(128) NOT NULL CHECK (seed_usage_type IN ('HCP_MASTER', 'CONTEXT_ITEM_MASTER')),
    encrypted_seed_hex       TEXT         NOT NULL,
    kms_encryption_key_id    VARCHAR(128) NOT NULL,
    kms_encryption_algorithm VARCHAR(128) NOT NULL CHECK (kms_encryption_algorithm IN ('SYMMETRIC_DEFAULT')),
    created_at               BIGINT       NOT NULL,
    last_modified            BIGINT       NOT NULL,
    version                  BIGINT       NOT NULL
);

-- Add index for `user_account_id` lookups
CREATE INDEX user_seed_data_user_account_id
    on custodial_wallet.user_seed_data (user_account_id);
