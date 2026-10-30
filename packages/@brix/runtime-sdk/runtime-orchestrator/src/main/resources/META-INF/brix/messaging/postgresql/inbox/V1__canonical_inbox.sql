-- Brix canonical messaging tables, platform schema v1 (root BRX-RS-MESSAGE-023, MESSAGE-025, MESSAGE-026;
-- Kafka design §14 K19): the inbox, for the reference DCP (PostgreSQL).
--
-- The platform applies this migration for every Owner whose manifest declares data.inbox ({{storageId}}_inbox),
-- with {{storageId}} replaced by its data.storageId, in a schema history table of its own, after the Owner's
-- own migrations. An Owner whose migrations already created the table keeps it, and its history starts at this
-- version. The runtime checks the columns, keys and indexes at startup (CanonicalMessagingTables). Later
-- platform versions add migrations next to this one; an applied migration is never edited.

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
