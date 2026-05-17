-- ============================================================================
-- V011: Create sys_tenant_config Table — Plugin-Level Tenant Configuration
-- ============================================================================
-- Phase 3, Task #14: Plugin-Scoped Tenant Configuration Store
--
-- Design Reference: v1.2-multi-tenant-design Section 2.3
-- Architecture Layer: Infrastructure (Flyway Migration)
--
-- Purpose:
--   Provides a key-value configuration store scoped per tenant per plugin.
--   Each plugin (e.g., "reservation", "medical") can define its own
--   configuration namespace without requiring schema migrations.
--
-- Schema Design:
--   - (tenant_id, config_namespace, config_key) is the natural composite key.
--   - config_value is JSONB to support complex structured values.
--   - config_type provides type hint for UI rendering and validation.
--   - is_sensitive marks values that require encryption/masking in UI.
--   - is_readonly marks values that only platform admins can modify.
--
-- Security:
--   - Sensitive config values should be encrypted at rest (app-level).
--   - Read access: any tenant member (for their own tenant).
--   - Write access: OWNER/ADMIN roles only.
-- ============================================================================

CREATE TABLE IF NOT EXISTS sys_tenant_config (
    id                BIGINT       PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL,

    config_namespace  VARCHAR(100) NOT NULL,
    config_key        VARCHAR(200) NOT NULL,
    config_value      JSONB        NOT NULL,
    config_type       VARCHAR(20)  NOT NULL DEFAULT 'STRING',

    description       VARCHAR(500),
    is_sensitive      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_readonly       BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by        BIGINT,

    CONSTRAINT uk_tenant_config
        UNIQUE (tenant_id, config_namespace, config_key),
    CONSTRAINT fk_tenant_config_tenant
        FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id),
    CONSTRAINT chk_config_type
        CHECK (config_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON', 'ENUM'))
);

CREATE INDEX IF NOT EXISTS idx_tenant_config_tenant
    ON sys_tenant_config(tenant_id);

CREATE INDEX IF NOT EXISTS idx_tenant_config_ns
    ON sys_tenant_config(tenant_id, config_namespace);

COMMENT ON TABLE sys_tenant_config IS 'Plugin-level tenant configuration key-value store';
COMMENT ON COLUMN sys_tenant_config.config_namespace IS 'Plugin namespace, e.g. platform, reservation, medical';
COMMENT ON COLUMN sys_tenant_config.config_key IS 'Configuration key within namespace, e.g. appointment.default_duration_min';
COMMENT ON COLUMN sys_tenant_config.config_value IS 'Configuration value as JSON (supports primitives and complex structures)';
COMMENT ON COLUMN sys_tenant_config.config_type IS 'Value type hint: STRING, NUMBER, BOOLEAN, JSON, ENUM';
COMMENT ON COLUMN sys_tenant_config.is_sensitive IS 'If true, value should be encrypted and masked in UI';
COMMENT ON COLUMN sys_tenant_config.is_readonly IS 'If true, only platform admins can modify';
