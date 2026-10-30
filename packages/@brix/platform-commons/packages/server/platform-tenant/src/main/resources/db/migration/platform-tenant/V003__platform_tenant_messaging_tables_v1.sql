-- Root BRX-RS-MESSAGE-026 / Kafka design §14 K19: bring platform-tenant's canonical messaging tables to the
-- platform schema v1 (runtime-orchestrator META-INF/brix/messaging/postgresql/canonical-messaging-v1.sql),
-- which the runtime checks at startup.

-- Outbox: number the messages in the order they were recorded (existing rows by occurred_at), so the
-- relay sends a partition key's messages in that order whatever the producers' clocks say (K13); a
-- discarded parked message keeps its row as DISCARDED (D33); indexes for the per-key check, replay by
-- event id and the cleanup of published rows (K14, K16).
ALTER TABLE platform_tenant_outbox ADD COLUMN seq BIGINT;
UPDATE platform_tenant_outbox o
   SET seq = numbered.n
  FROM (SELECT message_id, row_number() OVER (ORDER BY occurred_at, message_id) AS n FROM platform_tenant_outbox) numbered
 WHERE o.message_id = numbered.message_id;
ALTER TABLE platform_tenant_outbox ALTER COLUMN seq SET NOT NULL;
ALTER TABLE platform_tenant_outbox ALTER COLUMN seq ADD GENERATED ALWAYS AS IDENTITY;
SELECT setval(pg_get_serial_sequence('platform_tenant_outbox', 'seq'), COALESCE(MAX(seq), 0) + 1, false) FROM platform_tenant_outbox;
ALTER TABLE platform_tenant_outbox ADD CONSTRAINT uk_platform_tenant_outbox_seq UNIQUE (seq);

ALTER TABLE platform_tenant_outbox DROP CONSTRAINT chk_platform_tenant_outbox_status;
ALTER TABLE platform_tenant_outbox ADD CONSTRAINT chk_platform_tenant_outbox_status
    CHECK (status IN ('PENDING', 'IN_FLIGHT', 'PUBLISHED', 'PARKED', 'DISCARDED'));

CREATE INDEX idx_platform_tenant_outbox_key ON platform_tenant_outbox (partition_key, seq);
CREATE INDEX idx_platform_tenant_outbox_event ON platform_tenant_outbox (event_id);
CREATE INDEX idx_platform_tenant_outbox_published ON platform_tenant_outbox (status, published_at);

-- Inbox: an index for the cleanup of old deduplication records (K16).
CREATE INDEX idx_platform_tenant_inbox_processed ON platform_tenant_inbox (processed_at);
