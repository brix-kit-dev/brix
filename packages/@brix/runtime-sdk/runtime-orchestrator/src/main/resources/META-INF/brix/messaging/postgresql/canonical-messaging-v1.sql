-- Brix canonical messaging tables, platform schema v1 (root BRX-RS-MESSAGE-023, MESSAGE-025, MESSAGE-026;
-- Kafka design §14 K19), for the reference DCP (PostgreSQL).
--
-- Copy this file into the Owner's data.migrationLocation as one new versioned migration and replace every
-- {{storageId}} with the manifest's data.storageId. Keep the parts the manifest needs:
--   * the outbox when it declares data.outbox ({{storageId}}_outbox): the Owner publishes events or submits commands;
--   * the inbox when it declares data.inbox ({{storageId}}_inbox): the Owner subscribes or handles commands;
--   * the command idempotency table when it provides commands.
-- The runtime checks the columns, keys and indexes at startup (CanonicalMessagingTables) and refuses an
-- Owner whose tables differ. Later platform versions add migrations to this one; never edit an applied copy.
--
-- Retention (Kafka design D34): the runtime deletes published and discarded outbox rows after 14 days, and inbox
-- and command idempotency rows after 30 days, a batch at a time (brix.outbox.relay.retention.*). Parked rows stay
-- until an operator replays or discards them. A published row can be republished until it is deleted.

-- Messages the Owner sends. seq numbers them in the order they were recorded: the relay sends the
-- messages of one partition key in that order, whatever the producers' clocks say.
CREATE TABLE {{storageId}}_outbox (
    message_id          VARCHAR(64)  NOT NULL,
    seq                 BIGINT       GENERATED ALWAYS AS IDENTITY,
    event_id            VARCHAR(64)  NOT NULL,
    message_kind        VARCHAR(20)  NOT NULL,
    message_type        VARCHAR(128) NOT NULL,
    event_type          VARCHAR(128) NOT NULL,
    schema_version      VARCHAR(16)  NOT NULL,
    tenant_id           BIGINT,
    partition_key       VARCHAR(128) NOT NULL,
    payload             JSONB        NOT NULL,
    reliability         VARCHAR(20)  NOT NULL,
    producer_plugin_id  VARCHAR(128) NOT NULL,
    scope               VARCHAR(20)  NOT NULL,
    correlation_id      VARCHAR(128) NOT NULL,
    causation_id        VARCHAR(128),
    traceparent         VARCHAR(128),
    tracestate          VARCHAR(512),
    status              VARCHAR(20)  NOT NULL,
    occurred_at         TIMESTAMPTZ  NOT NULL,
    available_at        TIMESTAMPTZ  NOT NULL,
    attempt_count       INTEGER      NOT NULL DEFAULT 0,
    claim_owner         VARCHAR(128),
    claim_until         TIMESTAMPTZ,
    published_at        TIMESTAMPTZ,
    last_error_code     VARCHAR(128),
    created_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_{{storageId}}_outbox PRIMARY KEY (message_id),
    CONSTRAINT uk_{{storageId}}_outbox_seq UNIQUE (seq),
    CONSTRAINT chk_{{storageId}}_outbox_status
        CHECK (status IN ('PENDING', 'IN_FLIGHT', 'PUBLISHED', 'PARKED', 'DISCARDED')),
    CONSTRAINT chk_{{storageId}}_outbox_message_kind CHECK (message_kind IN ('EVENT', 'COMMAND')),
    CONSTRAINT chk_{{storageId}}_outbox_reliability CHECK (reliability IN ('CRITICAL', 'STANDARD')),
    CONSTRAINT chk_{{storageId}}_outbox_scope CHECK (
        (scope = 'TENANT' AND tenant_id IS NOT NULL)
        OR (scope = 'PLATFORM' AND tenant_id IS NULL)
    ),
    CONSTRAINT chk_{{storageId}}_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_{{storageId}}_outbox_key ON {{storageId}}_outbox (partition_key, seq);
CREATE INDEX idx_{{storageId}}_outbox_due ON {{storageId}}_outbox (status, available_at);
CREATE INDEX idx_{{storageId}}_outbox_event ON {{storageId}}_outbox (event_id);
CREATE INDEX idx_{{storageId}}_outbox_published ON {{storageId}}_outbox (status, published_at);

-- Deliveries the Owner's handlers have applied, written in the same transaction as their effects
-- (MESSAGE-025): (handler_id, message_id) is the deduplication key.
CREATE TABLE {{storageId}}_inbox (
    handler_id          VARCHAR(128) NOT NULL,
    message_id          VARCHAR(64)  NOT NULL,
    message_kind        VARCHAR(20)  NOT NULL,
    message_type        VARCHAR(128) NOT NULL,
    schema_version      VARCHAR(16)  NOT NULL,
    tenant_id           BIGINT,
    status              VARCHAR(20)  NOT NULL,
    processed_at        TIMESTAMPTZ  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_{{storageId}}_inbox PRIMARY KEY (handler_id, message_id),
    CONSTRAINT chk_{{storageId}}_inbox_message_kind CHECK (message_kind IN ('EVENT', 'COMMAND')),
    CONSTRAINT chk_{{storageId}}_inbox_status CHECK (status IN ('PROCESSING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX idx_{{storageId}}_inbox_processed ON {{storageId}}_inbox (processed_at);

-- Business idempotency of the commands the Owner handles (MESSAGE-014): a second command with the same
-- (handler, scope, key) is acknowledged without running the handler again.
CREATE TABLE {{storageId}}_command_idempotency (
    handler_id          VARCHAR(128) NOT NULL,
    idempotency_scope   VARCHAR(128) NOT NULL,
    idempotency_key     VARCHAR(128) NOT NULL,
    command_id          VARCHAR(64)  NOT NULL,
    command_type        VARCHAR(128) NOT NULL,
    schema_version      VARCHAR(16)  NOT NULL,
    tenant_id           BIGINT,
    status              VARCHAR(20)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_{{storageId}}_command_idempotency PRIMARY KEY (handler_id, idempotency_scope, idempotency_key),
    CONSTRAINT uk_{{storageId}}_command_idempotency_command UNIQUE (handler_id, command_id)
);

CREATE INDEX idx_{{storageId}}_command_idempotency_created ON {{storageId}}_command_idempotency (created_at);
