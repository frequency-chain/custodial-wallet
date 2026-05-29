-- noinspection SqlNoDataSourceInspectionForFile

CREATE TABLE user_key_data
(
    id                         BIGSERIAL    PRIMARY KEY,
    user_id                    VARCHAR(128) NOT NULL,
    public_key_hex             TEXT         NOT NULL,
    encrypted_private_key_hex  TEXT         NOT NULL,
    encrypted_private_key_type VARCHAR(128) NOT NULL,
    kms_encryption_key_id      VARCHAR(128) NOT NULL,
    kms_encryption_key_id_type VARCHAR(128) NOT NULL,
    created_at                 BIGINT       NOT NULL,
    last_modified              BIGINT       NOT NULL,
    version                    BIGINT       NOT NULL
);