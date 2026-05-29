CREATE TABLE user_account
(
    id                   BIGSERIAL    PRIMARY KEY,
    created_at           BIGINT       NOT NULL,
    last_modified        BIGINT       NOT NULL,
    version              BIGINT       NOT NULL
);

DROP TABLE user_key_data;
CREATE TABLE user_key_data
(
    id                         BIGSERIAL    PRIMARY KEY,
    user_account_id            BIGINT       NOT NULL     REFERENCES user_account,
    public_key_hex             TEXT         NOT NULL,
    encrypted_private_key_hex  TEXT         NOT NULL,
    encrypted_private_key_type VARCHAR(128) NOT NULL,
    kms_encryption_key_id      VARCHAR(128) NOT NULL,
    kms_encryption_key_id_type VARCHAR(128) NOT NULL,
    created_at                 BIGINT       NOT NULL,
    last_modified              BIGINT       NOT NULL,
    version                    BIGINT       NOT NULL
);

CREATE TABLE provider_external_user
(
    id                   BIGSERIAL    PRIMARY KEY,
    provider_msa_id      BIGINT       NOT NULL,
    provider_external_id VARCHAR(128) NOT NULL,
    user_key_data_id     BIGINT       NOT NULL     REFERENCES user_key_data,
    created_at           BIGINT       NOT NULL,
    last_modified        BIGINT       NOT NULL,
    version              BIGINT       NOT NULL,
    UNIQUE (provider_msa_id, provider_external_id)
);



CREATE TABLE provider_external_user_detail
(
    id                        BIGSERIAL    PRIMARY KEY,
    provider_external_user_id BIGINT       NOT NULL     REFERENCES provider_external_user,
    user_account_id           BIGINT       NOT NULL     REFERENCES user_account,
    user_detail_value         VARCHAR(128) NOT NULL,
    user_detail_type          VARCHAR(128) NOT NULL,
    created_at                BIGINT       NOT NULL,
    last_modified             BIGINT       NOT NULL,
    version                   BIGINT       NOT NULL,
    UNIQUE (provider_external_user_id, user_detail_value, user_detail_type)
);