-- ======== Deprecate columns in `provider_external_user_detail` ========

-- Make `user_detail_*` values nullable
ALTER TABLE provider_external_user_detail
ALTER COLUMN user_detail_value DROP NOT NULL,
ALTER COLUMN user_detail_type DROP NOT NULL;


-- ======== Add `user_identifier` table ========

-- This table will store emails and phone numbers (and possibly passkey public keys) used for login.
CREATE TABLE user_identifier
(
    id                     BIGSERIAL         PRIMARY KEY,
    value                  VARCHAR(128)      NOT NULL,
    type                   VARCHAR(128)      NOT NULL,
    created_at             BIGINT            NOT NULL,
    last_modified          BIGINT            NOT NULL,
    version                BIGINT            NOT NULL,
    UNIQUE (value, type)
);

-- Populate the table using normalized data from the `...user_detail` table
INSERT INTO user_identifier (value, type, created_at, last_modified, version)
SELECT user_detail_value, user_detail_type, min(created_at), max(last_modified), max(version)
FROM custodial_wallet.provider_external_user_detail
GROUP BY user_detail_value, user_detail_type;

-- Add a column to the `..._user_detail` table to reference the `user_identifier` table.
ALTER TABLE provider_external_user_detail
ADD COLUMN user_identifier_id BIGINT;

-- Populate the new column
UPDATE provider_external_user_detail AS user_detail
SET user_identifier_id = (
    SELECT user_identifier.id
    FROM user_identifier
    WHERE user_identifier.value = user_detail.user_detail_value
        AND user_identifier.type = user_detail.user_detail_type
);

-- Make `user_identifier_id` not nullable
ALTER TABLE provider_external_user_detail
ALTER COLUMN user_identifier_id SET NOT NULL;

-- Add foreign key constraint
ALTER TABLE provider_external_user_detail
ADD CONSTRAINT provider_external_user_detail_user_identifier_fk
FOREIGN KEY (user_identifier_id) REFERENCES user_identifier(id);


-- ======== Add `user_account_user_identifier` table ========

CREATE TABLE user_account_user_identifier
(
    user_account_id         BIGINT      NOT NULL REFERENCES user_account,
    user_identifier_id      BIGINT      NOT NULL REFERENCES user_identifier,
    PRIMARY KEY (user_account_id, user_identifier_id)
);

-- Insert the existing relationships based on `...user_detail`s
INSERT INTO user_account_user_identifier (user_account_id, user_identifier_id)
SELECT user_detail.user_account_id, user_identifier.id
FROM user_identifier
JOIN provider_external_user_detail AS user_detail
    ON user_detail.user_detail_value = user_identifier.value
    AND user_detail.user_detail_type = user_identifier.type
GROUP BY user_detail.user_account_id, user_identifier.id;
