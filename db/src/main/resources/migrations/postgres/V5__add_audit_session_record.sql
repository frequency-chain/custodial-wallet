CREATE TABLE audit_session_record
(
    id                        BIGSERIAL    PRIMARY KEY,
    session_id                VARCHAR(128) NOT NULL,
    flow                      VARCHAR(128) NOT NULL,
    state                     VARCHAR(128) NOT NULL,
    finalized_state           VARCHAR(128) NOT NULL,
    stack_trace               TEXT,
    created_at                BIGINT       NOT NULL,
    last_modified             BIGINT       NOT NULL,
    version                   BIGINT       NOT NULL,
    UNIQUE (session_id)
);