-- platform-audit adopts sys_platform_audit_log (super-admin §1.3a, platform-audit design §3.3).
--
-- platform-identity V001 created the table and V002 restored description to TEXT; both stay
-- untouched (root BRX-RS-DATA-013). This migration states the same definition, so on an existing
-- database it changes nothing, and on a fresh install either module may create the table first.

CREATE TABLE IF NOT EXISTS sys_platform_audit_log (
    id                       BIGINT       PRIMARY KEY,
    operator_identity_id     BIGINT,
    action                   VARCHAR(64)  NOT NULL,
    resource_type            VARCHAR(64)  NOT NULL,
    resource_id              VARCHAR(128),
    affected_tenants         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    description              TEXT,
    context                  JSONB,
    success                  BOOLEAN      NOT NULL DEFAULT TRUE,
    error_code               VARCHAR(64),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_platform_audit_time ON sys_platform_audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_platform_audit_operator ON sys_platform_audit_log(operator_identity_id);
