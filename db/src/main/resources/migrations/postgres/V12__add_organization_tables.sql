-- An external, trusted organization or social media company (e.g., MeWe) corresponding to one or more
-- frequency providers on chain.
CREATE TABLE organization
(
  id                   BIGSERIAL  PRIMARY KEY,
  display_name         TEXT       NOT NULL, -- e.g., "MeWe"
  short_code           TEXT       NOT NULL, -- e.g., "mewe"

  created_at           BIGINT     NOT NULL,
  last_modified        BIGINT     NOT NULL,
  version              BIGINT     NOT NULL
);

CREATE TABLE provider_frequency_account
(
  id                   BIGSERIAL  PRIMARY KEY,
  msa_id               BIGINT     NOT NULL, -- Corresponds to value on chain
  organization_id      BIGINT     NOT NULL     REFERENCES organization(id),

  created_at           BIGINT     NOT NULL,
  last_modified        BIGINT     NOT NULL,
  version              BIGINT     NOT NULL
);

CREATE TABLE organization_whitelisted_origin_descriptor
(
  id                   BIGSERIAL  PRIMARY KEY,
  organization_id      BIGINT     NOT NULL     REFERENCES organization(id),
  scheme               TEXT       NOT NULL,
  domain               TEXT       NOT NULL,

  created_at           BIGINT     NOT NULL,
  last_modified        BIGINT     NOT NULL,
  version              BIGINT     NOT NULL
);

CREATE TABLE organization_asset
(
  id                   BIGSERIAL  PRIMARY KEY,
  organization_id      BIGINT     NOT NULL     REFERENCES organization(id),
  asset_type           TEXT       NOT NULL,
  url                  TEXT       NOT NULL,

  created_at           BIGINT     NOT NULL,
  last_modified        BIGINT     NOT NULL,
  version              BIGINT     NOT NULL,

  -- A given organization can only have one asset for each type
  UNIQUE(organization_id, asset_type)
);
