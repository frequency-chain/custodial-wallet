CREATE TABLE user_derived_key_data
(
    id                       BIGSERIAL    PRIMARY KEY,
    user_seed_data_id        BIGINT       REFERENCES user_seed_data,
    derivation_path          TEXT         NOT NULL,
    derived_key_usage_type   VARCHAR(128) NOT NULL
        CHECK (derived_key_usage_type IN ('CONTEXT_ITEM', 'CONTEXT_GROUP', 'ON_CHAIN')),
    encrypted_key_hex        TEXT         NOT NULL,
    kms_encryption_key_id    VARCHAR(128) NOT NULL,
    kms_encryption_algorithm VARCHAR(128) NOT NULL
        CHECK (kms_encryption_algorithm IN ('SYMMETRIC_DEFAULT')),
    created_at               BIGINT       NOT NULL,
    last_modified            BIGINT       NOT NULL,
    version                  BIGINT       NOT NULL
);
