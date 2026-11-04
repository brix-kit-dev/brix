-- Brix canonical messaging tables, platform schema v3 (root BRX-RS-MANIFEST-018, MANIFEST-022, MESSAGE-026;
-- standard-link plan point 10a): the endpoint idempotency table, for the reference DCP (PostgreSQL).
--
-- The platform applies this migration for every Owner whose manifest declares an endpoint with idempotency.mode:
-- required ({{storageId}}_endpoint_idempotency), with {{storageId}} replaced by its data.storageId, in a schema history
-- table of its own, after the Owner's own migrations. The runtime checks the columns, keys and indexes at startup
-- (CanonicalMessagingTables). Later platform versions add migrations next to this one; an applied migration is never
-- edited.

-- The answers the runtime replays, one row per key: the first call that succeeded takes the key in the transaction of
-- its business write, so a call that fails leaves no row. entry_id is "<module>/<handlerId>", and idempotency_scope is
-- "<tenant>|<caller>", derived by the runtime. request_fingerprint tells a repeated request from other content under
-- the same key. trace_id is the first call's trace. expires_at is when the cleaning may remove the row; until then a
-- later call with the key is a replay or a conflict.
CREATE TABLE {{storageId}}_endpoint_idempotency (
    entry_id            VARCHAR(256) NOT NULL,
    idempotency_scope   VARCHAR(128) NOT NULL,
    idempotency_key     VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64)  NOT NULL,
    result_status       INTEGER,
    result_body         JSONB,
    tenant_id           BIGINT,
    trace_id            VARCHAR(32),
    created_at          TIMESTAMPTZ  NOT NULL,
    expires_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_{{storageId}}_endpoint_idempotency PRIMARY KEY (entry_id, idempotency_scope, idempotency_key)
);

CREATE INDEX idx_{{storageId}}_endpoint_idempotency_expiry ON {{storageId}}_endpoint_idempotency (expires_at);
