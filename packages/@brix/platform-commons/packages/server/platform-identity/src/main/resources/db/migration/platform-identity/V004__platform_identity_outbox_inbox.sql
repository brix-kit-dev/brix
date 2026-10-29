-- Root BRX-RS-MESSAGE-023, MESSAGE-026 / P4 design §4.1: platform-identity owns its canonical outbox and
-- inbox, with the same platform structure as platform-tenant's. Its events are written to the outbox in
-- the identity transaction (MESSAGE-022) and delivered by the runtime relay after commit.

CREATE TABLE IF NOT EXISTS platform_identity_outbox (
    message_id               VARCHAR(64)  PRIMARY KEY,
    event_id                 VARCHAR(64)  NOT NULL,
    message_kind             VARCHAR(20)  NOT NULL DEFAULT 'EVENT',
    message_type             VARCHAR(128) NOT NULL,
    event_type               VARCHAR(128) NOT NULL,
    schema_version           VARCHAR(16)  NOT NULL DEFAULT '1.0.0',
    tenant_id                BIGINT,
    partition_key            VARCHAR(128) NOT NULL,
    payload                  JSONB        NOT NULL,
    reliability              VARCHAR(20)  NOT NULL DEFAULT 'CRITICAL',
    producer_plugin_id       VARCHAR(128) NOT NULL DEFAULT 'platform-identity',
    scope                    VARCHAR(20)  NOT NULL DEFAULT 'PLATFORM',
    correlation_id           VARCHAR(128) NOT NULL,
    causation_id             VARCHAR(128),
    traceparent              VARCHAR(128),
    tracestate               VARCHAR(512),
    status                   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    occurred_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempt_count            INTEGER      NOT NULL DEFAULT 0,
    claim_owner              VARCHAR(128),
    claim_until              TIMESTAMPTZ,
    published_at             TIMESTAMPTZ,
    last_error_code          VARCHAR(128),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_platform_identity_outbox_status CHECK (status IN ('PENDING', 'IN_FLIGHT', 'PUBLISHED', 'PARKED')),
    CONSTRAINT chk_platform_identity_outbox_message_kind CHECK (message_kind IN ('EVENT', 'COMMAND')),
    CONSTRAINT chk_platform_identity_outbox_scope CHECK (
        (scope = 'TENANT' AND tenant_id IS NOT NULL)
        OR (scope IN ('PLATFORM', 'GLOBAL') AND tenant_id IS NULL)
    ),
    CONSTRAINT chk_platform_identity_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_platform_identity_outbox_due ON platform_identity_outbox(status, available_at);
CREATE INDEX IF NOT EXISTS idx_platform_identity_outbox_claim ON platform_identity_outbox(claim_owner, claim_until);

CREATE TABLE IF NOT EXISTS platform_identity_inbox (
    handler_id               VARCHAR(128) NOT NULL,
    message_id               VARCHAR(64)  NOT NULL,
    message_kind             VARCHAR(20)  NOT NULL DEFAULT 'EVENT',
    message_type             VARCHAR(128) NOT NULL,
    schema_version           VARCHAR(16)  NOT NULL DEFAULT '1.0.0',
    tenant_id                BIGINT,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'PROCESSED',
    processed_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_platform_identity_inbox PRIMARY KEY (handler_id, message_id),
    CONSTRAINT chk_platform_identity_inbox_message_kind CHECK (message_kind IN ('EVENT', 'COMMAND')),
    CONSTRAINT chk_platform_identity_inbox_status CHECK (status IN ('PROCESSING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_platform_identity_inbox_tenant ON platform_identity_inbox(tenant_id);
