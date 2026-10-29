-- Root BRX-RS-MESSAGE-023, MESSAGE-025, MESSAGE-026 / P4 design §4.2: platform-audit owns a canonical
-- inbox, with the same platform structure as platform-tenant's and platform-identity's. The runtime
-- records (handler_id, message_id) here in the same transaction as the audit record it delivers, so a
-- redelivered identity audit fact is recorded once. platform-audit publishes nothing and has no outbox.

CREATE TABLE IF NOT EXISTS platform_audit_inbox (
    handler_id               VARCHAR(128) NOT NULL,
    message_id               VARCHAR(64)  NOT NULL,
    message_kind             VARCHAR(20)  NOT NULL DEFAULT 'EVENT',
    message_type             VARCHAR(128) NOT NULL,
    schema_version           VARCHAR(16)  NOT NULL DEFAULT '1.0.0',
    tenant_id                BIGINT,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'PROCESSED',
    processed_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_platform_audit_inbox PRIMARY KEY (handler_id, message_id),
    CONSTRAINT chk_platform_audit_inbox_message_kind CHECK (message_kind IN ('EVENT', 'COMMAND')),
    CONSTRAINT chk_platform_audit_inbox_status CHECK (status IN ('PROCESSING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_platform_audit_inbox_tenant ON platform_audit_inbox(tenant_id);
