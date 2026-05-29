-- An application attached to a provider account and through that an overall organization.
-- Organizations therefore have a list of application under their organization.
-- These assets and origins are used first and the organization level is a fallback.
CREATE TABLE provider_application
(
  id                                BIGSERIAL  PRIMARY KEY,
  provider_frequency_account_id     BIGSERIAL  NOT NULL         REFERENCES provider_frequency_account(id),
  verified_credential_url           TEXT       NOT NULL, -- e.g., "applicationurl.com
  display_name                      TEXT       NOT NULL, -- e.g., "MeWe"
  short_code                        TEXT       NOT NULL, -- e.g., "mewe"

  created_at                        BIGINT     NOT NULL,
  last_modified                     BIGINT     NOT NULL,
  version                           BIGINT     NOT NULL
);

CREATE TABLE provider_application_whitelisted_origin_descriptor
(
  id                        BIGSERIAL  PRIMARY KEY,
  provider_application_id   BIGINT     NOT NULL         REFERENCES provider_application(id),
  scheme                    TEXT       NOT NULL,
  domain                    TEXT       NOT NULL,

  created_at                BIGINT     NOT NULL,
  last_modified             BIGINT     NOT NULL,
  version                   BIGINT     NOT NULL
);

CREATE TABLE provider_application_asset
(
  id                        BIGSERIAL  PRIMARY KEY,
  provider_application_id   BIGINT     NOT NULL     REFERENCES provider_application(id),
  asset_type                TEXT       NOT NULL,
  url                       TEXT       NOT NULL,

  created_at                BIGINT     NOT NULL,
  last_modified             BIGINT     NOT NULL,
  version                   BIGINT     NOT NULL,

  -- A given application can only have one asset for each type
  UNIQUE(provider_application_id, asset_type)
);
