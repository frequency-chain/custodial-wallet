create table audit_session_record
(
    id              bigserial    primary key,
    session_id      varchar(128) not null unique,
    flow            varchar(128) not null,
    state           varchar(128) not null,
    finalized_state varchar(128) not null,
    stack_trace     text,
    created_at      bigint       not null,
    last_modified   bigint       not null,
    version         bigint       not null
);

create table flyway_schema_history
(
    installed_rank integer                 not null
        constraint flyway_schema_history_pk
            primary key,
    version        varchar(50),
    description    varchar(200)            not null,
    type           varchar(20)             not null,
    script         varchar(1000)           not null,
    checksum       integer,
    installed_by   varchar(100)            not null,
    installed_on   timestamp default now() not null,
    execution_time integer                 not null,
    success        boolean                 not null
);

create table user_account
(
    id            bigserial
        primary key,
    created_at    bigint not null,
    last_modified bigint not null,
    version       bigint not null
);

create table user_seed_data
(
    id                       bigserial primary key,
    user_account_id          bigint       not null references user_account,
    seed_usage_type          varchar(128) not null check (seed_usage_type in ('HCP_MASTER', 'CONTEXT_ITEM_MASTER')),
    encrypted_seed_prase_hex text,
    encrypted_seed_hex       text         not null,
    kms_encryption_key_id    varchar(128) not null,
    kms_encryption_algorithm varchar(128) not null check (kms_encryption_algorithm in ('SYMMETRIC_DEFAULT')),
    created_at               bigint       not null,
    last_modified            bigint       not null,
    version                  bigint       not null
);

create table user_key_data
(
    id                         bigserial
        primary key,
    user_account_id            bigint                                            not null
        references user_account,
    public_key_hex             text                                              not null,
    encrypted_private_key_hex  text                                              not null,
    encrypted_private_key_type varchar(128)                                      not null,
    kms_encryption_key_id      varchar(128)                                      not null,
    kms_encryption_key_id_type varchar(128)                                      not null,
    user_seed_data_id          bigint
        references user_seed_data,
    created_at                 bigint                                            not null,
    last_modified              bigint                                            not null,
    version                    bigint                                            not null,
    key_usage_type             varchar(128) default 'ACCOUNT'::character varying not null
);

alter table user_key_data
    owner to custodial_wallet_app;

create index user_key_data_hex_lowercased
    on user_key_data (lower(public_key_hex));

create index user_account_id_key_usage_type
    on user_key_data (user_account_id, key_usage_type);

create table provider_external_user
(
    id                   bigserial
        primary key,
    provider_msa_id      bigint       not null,
    provider_external_id varchar(128) not null,
    user_key_data_id     bigint       not null
        references user_key_data,
    created_at           bigint       not null,
    last_modified        bigint       not null,
    version              bigint       not null,
    unique (provider_msa_id, provider_external_id)
);

create table provider_external_user_detail
(
    id                        bigserial
        primary key,
    provider_external_user_id bigint            not null
        references provider_external_user,
    user_account_id           bigint            not null
        references user_account,
    user_detail_value         varchar(128)      not null,
    user_detail_type          varchar(128)      not null,
    created_at                bigint            not null,
    last_modified             bigint            not null,
    version                   bigint            not null,
    user_detail_priority      integer default 1 not null,
    constraint provider_external_user_detail_provider_external_user_id_use_key
        unique (provider_external_user_id, user_detail_value, user_detail_type)
);

create index concurrently provider_external_user_detail_user_detail_value_and_user_detail_type
    on custodial_wallet.provider_external_user_detail(user_detail_value, user_detail_type);

create table user_identifier
(
    id              bigserial           primary key,
    value           varchar(128)        not null,
    type            varchar(128)        not null,
    created_at      bigint              not null,
    last_modified   bigint              not null,
    version         bigint              not null,
    verified_date   bigint,
    constraint user_identifier_value_type_key unique (value, type)
);

create table user_account_user_identifier
(
    user_account_id         bigint      not null references user_account(id),
    user_identifier_id      bigint      not null references user_identifier(id),
    constraint user_account_user_identifier_pkey primary key (user_account_id, user_identifier_id),
    constraint user_account_user_identifier_user_identifier_id_key unique (user_identifier_id),
);

create index concurrently user_account_user_identifier_user_account_id
    on custodial_wallet.user_account_user_identifier(user_account_id);

create table wallet
(
    id                                          bigserial     primary key,
    user_account_id                             bigint        not null references user_account(id),
    public_key_base64_url                       text          not null,
    created_at                                  bigint        not null,
    last_modified                               bigint        not null,
    version                                     bigint        not null
);

create table credential
(
    id                                bigserial  primary key,
    user_account_id                   bigint     references user_account(id),
    wallet_id                         bigint     references wallet(id),
    authenticator_uuid                uuid       not null,
    credential_id_base64_url          text       not null,
    public_key_cose                   text       not null,
    compressed_public_key_base64_url  text       not null,
    sign_count                        bigint     not null,
    backup_eligible                   boolean    not null,
    backed_up                         boolean    not null,
    created_at                        bigint     not null,
    last_modified                     bigint     not null,
    version                           bigint     not null,
    constraint user_account_id_xor_wallet_id
        check ((user_account_id is not null and wallet_id is null)
            or (user_account_id is null and wallet_id is not null)),
    unique (credential_id_base64_url),
);

create table credential_transport
(
    id                         bigserial  primary key,
    credential_id              bigint     not null references credential(id),
    transport                  text       not null,
    created_at                 bigint     not null,
    last_modified              bigint     not null,
    version                    bigint     not null,
    unique (credential_id, transport)
);

create table wallet_metadata
(
    id                                          bigserial     primary key,
    wallet_id                                   bigint        not null references wallet(id),
    credential_id                               bigint        not null references credential(id)
    signature_of_credential_base64_url          text          not null,
    credential_signature_of_account_base64_url  text,
    created_at                                  bigint        not null,
    last_modified                               bigint        not null,
    version                                     bigint        not null,
);

create table organization
(
  id                   bigserial  primary key,
  display_name         text       not null,
  short_code           text       not null,
  created_at           bigint     not null,
  last_modified        bigint     not null,
  version              bigint     not null
);

create table provider_frequency_account
(
  id                   bigserial  primary key,
  msa_id               bigint     not null,
  organization_id      bigint     not null     references organization(id),
  created_at           bigint     not null,
  last_modified        bigint     not null,
  version              bigint     not null
  constraint provider_frequency_account_msa_id_key unique (msa_id),
);

create table organization_whitelisted_origin_descriptor
(
  id                   bigserial  primary key,
  organization_id      bigint     not null     references organization(id),
  scheme               text       not null,
  domain               text       not null,
  created_at           bigint     not null,
  last_modified        bigint     not null,
  version              bigint     not null
);

create table organization_asset
(
  id                   bigserial  primary key,
  organization_id      bigint     not null     references organization(id),
  asset_type           text       not null,
  url                  text       not null,
  created_at           bigint     not null,
  last_modified        bigint     not null,
  version              bigint     not null,
  unique(organization_id, asset_type)
);

create table provider_application
(
    id                              bigserial  primary key,
    provider_frequency_account_id   bigserial  not null     references provider_frequency_account(id),
    verified_credential_url         text       not null,
    display_name                    text       not null,
    short_code                      text       not null,
    created_at                      bigint     not null,
    last_modified                   bigint     not null,
    version                         bigint     not null
);

create index concurrently provider_application_provider_frequency_account_id
    on custodial_wallet.provider_application(provider_frequency_account_id);

create table provider_application_whitelisted_origin_descriptor
(
    id                          bigserial  primary key,
    provider_application_id     bigint     not null     references provider_application(id),
    scheme                      text       not null,
    domain                      text       not null,
    created_at                  bigint     not null,
    last_modified               bigint     not null,
    version                     bigint     not null
);

create table provider_application_asset
(
    id                          bigserial  primary key,
    provider_application_id     bigint     not null     references provider_application(id),
    asset_type                  text       not null,
    url                         text       not null,
    created_at                  bigint     not null,
    last_modified               bigint     not null,
    version                     bigint     not null,
    unique(provider_application_id, asset_type)
);

create index concurrently provider_application_provider_frequency_account_id_verified_credential_url
  on custodial_wallet.provider_application(provider_frequency_account_id, verified_credential_url);

create index concurrently user_account_id
    on custodial_wallet.user_key_data(user_account_id);

create table opt_in
(
    id                         bigserial  primary key,
    user_account_id            bigint       not null     references user_account,
    opt_in_type                varchar(128) not null,
    is_opted_in                boolean      not null,
    created_at                 bigint       not null,
    last_modified              bigint       not null,
    version                    bigint       not null,
    unique(user_account_id, opt_in_type)
);

create index opt_in_user_account_id
    on custodial_wallet.opt_in(user_account_id);

create index user_seed_data_user_account_id
    on custodial_wallet.user_seed_data (user_account_id);

create table user_derived_key_data
(
    id                       bigserial    primary key,
    user_seed_data_id        bigint       references user_seed_data,
    derivation_path          text         not null,
    derived_key_usage_type   varchar(128) not null check (derived_key_usage_type in ('CONTEXT_ITEM', 'CONTEXT_GROUP', 'ON_CHAIN')),
    encrypted_key_hex        text         not null,
    kms_encryption_key_id    varchar(128) not null,
    kms_encryption_algorithm varchar(128) not null check (kms_encryption_algorithm in ('SYMMETRIC_DEFAULT')),
    created_at               bigint       not null,
    last_modified            bigint       not null,
    version                  bigint       not null
);
