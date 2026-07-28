-- M5 typed Command substrate: business idempotency keys for command handlers.
-- Forward-only: commands reuse platform_tenant_inbox with message_kind=COMMAND.

CREATE TABLE IF NOT EXISTS platform_tenant_command_idempotency (
    handler_id          VARCHAR(128) NOT NULL,
    idempotency_scope   VARCHAR(128) NOT NULL,
    idempotency_key     VARCHAR(128) NOT NULL,
    command_id          VARCHAR(64)  NOT NULL,
    command_type        VARCHAR(128) NOT NULL,
    schema_version      VARCHAR(16)  NOT NULL,
    tenant_id           BIGINT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'RESERVED',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (handler_id, idempotency_scope, idempotency_key),
    CONSTRAINT uq_platform_tenant_command_idempotency_command
        UNIQUE (handler_id, command_id),
    CONSTRAINT chk_platform_tenant_command_idempotency_status
        CHECK (status IN ('RESERVED', 'COMPLETED'))
);

CREATE INDEX IF NOT EXISTS idx_platform_tenant_command_idempotency_tenant
    ON platform_tenant_command_idempotency(tenant_id, created_at);

COMMENT ON TABLE platform_tenant_command_idempotency IS
    'Business idempotency keys for reliable typed Command handlers';
COMMENT ON COLUMN platform_tenant_command_idempotency.command_id IS
    'Stable Command identity retained across retry and replay';
