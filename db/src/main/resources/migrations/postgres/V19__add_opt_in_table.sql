CREATE TABLE opt_in
(
    id                         BIGSERIAL    PRIMARY KEY,
    user_account_id            BIGINT       NOT NULL     REFERENCES user_account,
    opt_in_type                VARCHAR(128) NOT NULL,
    is_opted_in                BOOLEAN      NOT NULL,
    created_at                 BIGINT       NOT NULL,
    last_modified              BIGINT       NOT NULL,
    version                    BIGINT       NOT NULL,

    -- A given user account can only have one entry for each opt in type
    UNIQUE(user_account_id, opt_in_type)
);

-- Add index for foreign key in `opt_in`
CREATE INDEX opt_in_user_account_id
    on custodial_wallet.opt_in(user_account_id);