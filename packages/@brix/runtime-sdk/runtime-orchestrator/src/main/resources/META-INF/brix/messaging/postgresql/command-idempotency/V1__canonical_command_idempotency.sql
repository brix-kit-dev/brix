-- Brix canonical messaging tables, platform schema v1 (root BRX-RS-MESSAGE-023, MESSAGE-025, MESSAGE-026;
-- Kafka design §14 K19): the command idempotency table, for the reference DCP (PostgreSQL).
--
-- The platform applies this migration for every Owner whose manifest provides a command
-- ({{storageId}}_command_idempotency), with {{storageId}} replaced by its data.storageId, in a schema history table of its own, after the Owner's
-- own migrations. An Owner whose migrations already created the table keeps it, and its history starts at this
-- version. The runtime checks the columns, keys and indexes at startup (CanonicalMessagingTables). Later
-- platform versions add migrations next to this one; an applied migration is never edited.

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
