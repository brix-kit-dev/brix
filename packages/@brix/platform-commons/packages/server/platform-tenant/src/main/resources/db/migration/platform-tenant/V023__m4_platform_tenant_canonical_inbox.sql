-- M4 Consumer persistent Inbox and first reliable FIRST_OWNER projection.
-- Forward-only: keep V021 records and add the canonical fields required by
-- runtime-shell 3.0.10 without rewriting historical migrations.

ALTER TABLE platform_tenant_inbox
    ADD COLUMN IF NOT EXISTS message_kind VARCHAR(20) NOT NULL DEFAULT 'EVENT',
    ADD COLUMN IF NOT EXISTS schema_version VARCHAR(16),
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

UPDATE platform_tenant_inbox
   SET schema_version = '1.0.0'
 WHERE schema_version IS NULL;

UPDATE platform_tenant_inbox
   SET created_at = processed_at
 WHERE created_at IS NULL;

ALTER TABLE platform_tenant_inbox
    ALTER COLUMN schema_version SET NOT NULL;

ALTER TABLE platform_tenant_inbox DROP CONSTRAINT IF EXISTS chk_platform_tenant_inbox_message_kind;
ALTER TABLE platform_tenant_inbox ADD CONSTRAINT chk_platform_tenant_inbox_message_kind CHECK (
    message_kind IN ('EVENT', 'COMMAND')
);

ALTER TABLE platform_tenant_inbox DROP CONSTRAINT IF EXISTS chk_platform_tenant_inbox_status;
ALTER TABLE platform_tenant_inbox ADD CONSTRAINT chk_platform_tenant_inbox_status CHECK (
    status IN ('PROCESSED', 'FAILED')
);

CREATE INDEX IF NOT EXISTS idx_platform_tenant_inbox_tenant
    ON platform_tenant_inbox(tenant_id, processed_at);

CREATE TABLE IF NOT EXISTS platform_tenant_first_owner_projection (
    tenant_id       BIGINT       PRIMARY KEY,
    message_id      VARCHAR(64)  NOT NULL UNIQUE,
    owner_member_id BIGINT       NOT NULL,
    profile_id      BIGINT       NOT NULL,
    invitation_id   BIGINT       NOT NULL,
    projected_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_first_owner_projection_tenant
        FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)
);

CREATE INDEX IF NOT EXISTS idx_first_owner_projection_message
    ON platform_tenant_first_owner_projection(message_id);

COMMENT ON COLUMN platform_tenant_inbox.message_kind IS
    'Canonical consumed message kind: EVENT or COMMAND';
COMMENT ON COLUMN platform_tenant_inbox.schema_version IS
    'Consumed event schema version declared by the active manifest';
COMMENT ON TABLE platform_tenant_first_owner_projection IS
    'Minimal business side effect produced by TenantFirstOwnerAccepted consumption';
