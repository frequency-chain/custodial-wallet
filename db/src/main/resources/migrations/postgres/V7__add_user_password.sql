CREATE TABLE user_password
(
    id                          BIGSERIAL    PRIMARY KEY,
    user_account_id             BIGINT       NOT NULL     REFERENCES user_account,
    key_derivation_algorithm    VARCHAR(128) NOT NULL,
    hash                        VARCHAR(128) NOT NULL,
    created_at                  BIGINT       NOT NULL,
    last_modified               BIGINT       NOT NULL,
    version                     BIGINT       NOT NULL,
    UNIQUE (user_account_id)
);