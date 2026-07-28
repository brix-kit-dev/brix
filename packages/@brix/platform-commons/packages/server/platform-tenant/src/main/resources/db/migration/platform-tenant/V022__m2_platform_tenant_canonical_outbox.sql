-- M2 Producer transactional Outbox: extend the platform-tenant owner outbox
-- to the canonical producer envelope shape without rewriting V021.

ALTER TABLE platform_tenant_outbox
    ADD COLUMN IF NOT EXISTS message_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS message_kind VARCHAR(20) NOT NULL DEFAULT 'EVENT',
    ADD COLUMN IF NOT EXISTS message_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS producer_plugin_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS scope VARCHAR(20) NOT NULL DEFAULT 'TENANT',
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS causation_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS traceparent VARCHAR(128),
    ADD COLUMN IF NOT EXISTS tracestate VARCHAR(512),
    ADD COLUMN IF NOT EXISTS available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS claim_owner VARCHAR(128),
    ADD COLUMN IF NOT EXISTS claim_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(128),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

UPDATE platform_tenant_outbox
   SET message_id = event_id
 WHERE message_id IS NULL;

UPDATE platform_tenant_outbox
   SET message_type = event_type
 WHERE message_type IS NULL;

UPDATE platform_tenant_outbox
   SET producer_plugin_id = 'platform-tenant'
 WHERE producer_plugin_id IS NULL;

UPDATE platform_tenant_outbox
   SET correlation_id = event_id
 WHERE correlation_id IS NULL;

UPDATE platform_tenant_outbox
   SET available_at = occurred_at
 WHERE available_at IS NULL;

UPDATE platform_tenant_outbox
   SET created_at = occurred_at
 WHERE created_at IS NULL;

ALTER TABLE platform_tenant_outbox
    ALTER COLUMN message_id SET NOT NULL,
    ALTER COLUMN message_type SET NOT NULL,
    ALTER COLUMN producer_plugin_id SET NOT NULL,
    ALTER COLUMN correlation_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_platform_tenant_outbox_message_id
    ON platform_tenant_outbox(message_id);

ALTER TABLE platform_tenant_outbox DROP CONSTRAINT IF EXISTS chk_platform_tenant_outbox_status;
ALTER TABLE platform_tenant_outbox ADD CONSTRAINT chk_platform_tenant_outbox_status CHECK (
    status IN ('PENDING', 'IN_FLIGHT', 'PUBLISHED', 'FAILED', 'PARKED')
);

ALTER TABLE platform_tenant_outbox DROP CONSTRAINT IF EXISTS chk_platform_tenant_outbox_message_kind;
ALTER TABLE platform_tenant_outbox ADD CONSTRAINT chk_platform_tenant_outbox_message_kind CHECK (
    message_kind IN ('EVENT', 'COMMAND')
);

ALTER TABLE platform_tenant_outbox DROP CONSTRAINT IF EXISTS chk_platform_tenant_outbox_scope;
ALTER TABLE platform_tenant_outbox ADD CONSTRAINT chk_platform_tenant_outbox_scope CHECK (
    (scope = 'TENANT' AND tenant_id IS NOT NULL)
 OR (scope = 'PLATFORM' AND tenant_id IS NULL)
);

ALTER TABLE platform_tenant_outbox DROP CONSTRAINT IF EXISTS chk_platform_tenant_outbox_attempt_count;
ALTER TABLE platform_tenant_outbox ADD CONSTRAINT chk_platform_tenant_outbox_attempt_count CHECK (
    attempt_count >= 0
);

CREATE INDEX IF NOT EXISTS idx_platform_tenant_outbox_due
    ON platform_tenant_outbox(status, available_at, occurred_at);

CREATE INDEX IF NOT EXISTS idx_platform_tenant_outbox_claim
    ON platform_tenant_outbox(status, claim_until);

COMMENT ON COLUMN platform_tenant_outbox.message_id IS
    'Canonical message identity; equals event_id for M2 event records';
COMMENT ON COLUMN platform_tenant_outbox.producer_plugin_id IS
    'Runtime verified producer plugin id';
COMMENT ON COLUMN platform_tenant_outbox.scope IS
    'Canonical envelope scope: TENANT or PLATFORM';
COMMENT ON COLUMN platform_tenant_outbox.traceparent IS
    'W3C trace context propagated with the canonical envelope';
