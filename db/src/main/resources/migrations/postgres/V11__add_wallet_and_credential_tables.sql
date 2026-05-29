CREATE TABLE wallet
(
    id                                          BIGSERIAL     PRIMARY KEY,
    user_account_id                             BIGINT        NOT NULL REFERENCES user_account(id),
    -- The public key native to the blockchain (unrelated to the credential), i.e., "Seed Phrase Public Key"
    public_key_base64_url                       TEXT          NOT NULL,
    -- The compressed credential (P256) public key signed by the wallet account private key (SR25519)
    signature_of_credential_base64_url          TEXT          NOT NULL,
    -- The wallet account public key (SR25519) signed by the credential (P256) private key (optional)
    credential_signature_of_account_base64_url  TEXT,

    created_at                                  BIGINT        NOT NULL,
    last_modified                               BIGINT        NOT NULL,
    version                                     BIGINT        NOT NULL
);

CREATE TABLE credential
 (
    id                                BIGSERIAL  PRIMARY KEY,
    user_account_id                   BIGINT     REFERENCES user_account(id),
    wallet_id                         BIGINT     REFERENCES wallet(id),
    authenticator_uuid                UUID       NOT NULL,
    credential_id_base64_url          TEXT       NOT NULL,
    public_key_cose                   TEXT       NOT NULL,
    compressed_public_key_base64_url  TEXT       NOT NULL,
    sign_count                        BIGINT     NOT NULL,
    backup_eligible                   BOOLEAN    NOT NULL,
    backed_up                         BOOLEAN    NOT NULL,

    created_at                        BIGINT     NOT NULL,
    last_modified                     BIGINT     NOT NULL,
    version                           BIGINT     NOT NULL,

    -- A given credential must be linked to either a `user_account` (for login) OR
    -- a `wallet` (for passkey wallet) but not both.
    CONSTRAINT user_account_id_xor_wallet_id
        CHECK ((user_account_id IS NOT NULL AND wallet_id IS NULL)
            OR (user_account_id IS NULL AND wallet_id IS NOT NULL)),
    -- Per the WebAuthN spec, `credentialId` is globally unique
    UNIQUE (credential_id_base64_url),
    -- Wallets and credentials (e.g., passkeys) are 1:1
    UNIQUE (wallet_id)
);

-- Each credential may have an associated `Set` of transports
CREATE TABLE credential_transport
(
    id                         BIGSERIAL  PRIMARY KEY,
    credential_id              BIGINT     NOT NULL REFERENCES credential(id),
    transport                  TEXT       NOT NULL,  -- E.g., 'internal', 'nfc', 'usb'

    created_at                 BIGINT     NOT NULL,
    last_modified              BIGINT     NOT NULL,
    version                    BIGINT     NOT NULL,

    UNIQUE (credential_id, transport)
);
