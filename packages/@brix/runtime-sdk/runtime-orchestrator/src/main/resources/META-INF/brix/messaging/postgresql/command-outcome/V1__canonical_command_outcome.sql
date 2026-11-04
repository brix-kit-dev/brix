-- Brix canonical messaging tables, platform schema v1 (root BRX-RS-MESSAGE-013, MESSAGE-014, MESSAGE-026;
-- standard-link plan point 7): the command outcome table, for the reference DCP (PostgreSQL).
--
-- The platform applies this migration for every Owner whose manifest provides a command
-- ({{storageId}}_command_outcome), with {{storageId}} replaced by its data.storageId, in a schema history table of its own,
-- after the Owner's own migrations. The runtime checks the columns, keys and indexes at startup
-- (CanonicalMessagingTables). Later platform versions add migrations next to this one; an applied migration is never
-- edited.

-- What became of the commands the Owner handled without taking their idempotency key, one row per command: a
-- duplicate of the command that holds the key, a conflict with it, a rejection, or a discard of a command the
-- Owner had parked. A command that takes its key has its row in {{storageId}}_command_idempotency instead. The
-- sender reads both by command id; idempotency_scope tells which sender and tenant may read the row.
CREATE TABLE {{storageId}}_command_outcome (
    handler_id          VARCHAR(128) NOT NULL,
    command_id          VARCHAR(64)  NOT NULL,
    idempotency_scope   VARCHAR(128) NOT NULL,
    idempotency_key     VARCHAR(128) NOT NULL,
    outcome             VARCHAR(20)  NOT NULL,
    error_code          VARCHAR(128),
    holder_command_id   VARCHAR(64),
    tenant_id           BIGINT,
    created_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_{{storageId}}_command_outcome PRIMARY KEY (handler_id, command_id),
    CONSTRAINT chk_{{storageId}}_command_outcome_outcome
        CHECK (outcome IN ('DUPLICATE', 'CONFLICT', 'REJECTED', 'DISCARDED'))
);

CREATE INDEX idx_{{storageId}}_command_outcome_created ON {{storageId}}_command_outcome (created_at);
