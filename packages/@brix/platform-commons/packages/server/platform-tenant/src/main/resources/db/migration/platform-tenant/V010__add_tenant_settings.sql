-- ============================================================================
-- V010: Extend sys_tenant Table — Tenant-Level Configuration Fields
-- ============================================================================
-- Phase 3, Task #13: Tenant Settings Extension
--
-- Design Reference: v1.2-multi-tenant-design Section 2.3
-- Architecture Layer: Infrastructure (Flyway Migration)
--
-- Purpose:
--   Add tenant-level default configuration columns to sys_tenant.
--   These fields form the "Level 2" of the three-tier configuration model:
--     Level 3 (highest): User Preferences  (biz_user_profile.preferences JSONB)
--     Level 2 (middle):  Tenant Settings   (sys_tenant columns + sys_tenant_config)
--     Level 1 (lowest):  Platform Defaults (application.yml / sys_platform_config)
--
--   Effective value = userPreference ?? tenantConfig ?? platformDefault
--
-- Security:
--   - Sensitive fields (password_policy, mfa_policy, allowed_login_methods)
--     must be audited on change via biz_audit_log.
--   - Only OWNER/ADMIN roles may modify these settings.
-- ============================================================================

-- Regional & Localization
ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS default_locale
    VARCHAR(20) NOT NULL DEFAULT 'zh-CN';

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS default_timezone
    VARCHAR(50) NOT NULL DEFAULT 'UTC';

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS default_date_format
    VARCHAR(20) NOT NULL DEFAULT 'YYYY-MM-DD';

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS default_time_format
    VARCHAR(5) NOT NULL DEFAULT '24h';

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS default_currency
    VARCHAR(10) NOT NULL DEFAULT 'CNY';

-- Appearance
ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS default_theme
    VARCHAR(10) NOT NULL DEFAULT 'light';

-- Security Policies
ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS session_timeout_min
    INT NOT NULL DEFAULT 60;

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS mfa_policy
    VARCHAR(20) NOT NULL DEFAULT 'optional';

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS allowed_login_methods
    JSONB NOT NULL DEFAULT '["phone_sms", "email_password"]';

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS password_policy
    JSONB NOT NULL DEFAULT '{"minLength":8,"requireUppercase":false,"requireLowercase":true,"requireNumbers":true,"requireSpecialChars":false,"maxAgeDays":0,"historyCount":0}';

-- Notification
ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS notification_channels
    JSONB NOT NULL DEFAULT '["in_app"]';

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS business_hours
    JSONB NOT NULL DEFAULT '{}';

-- Extensible settings catch-all (for future fields without migration)
ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS settings
    JSONB NOT NULL DEFAULT '{}';

-- Branding (basic fields stored on sys_tenant for fast access)
ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS logo_url
    VARCHAR(512);

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS favicon_url
    VARCHAR(512);

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS primary_color
    VARCHAR(20) NOT NULL DEFAULT '#1976d2';

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS secondary_color
    VARCHAR(20);

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS login_page_title
    VARCHAR(200);

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS login_page_subtitle
    VARCHAR(500);

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS login_page_bg_url
    VARCHAR(512);

COMMENT ON COLUMN sys_tenant.default_locale IS 'Default locale for tenant members, e.g. zh-CN, ja-JP, en-US';
COMMENT ON COLUMN sys_tenant.default_timezone IS 'Default timezone, stored as an IANA timezone ID; platform default is UTC';
COMMENT ON COLUMN sys_tenant.default_date_format IS 'Default date display format, e.g. YYYY-MM-DD';
COMMENT ON COLUMN sys_tenant.default_time_format IS 'Time format: 12h or 24h';
COMMENT ON COLUMN sys_tenant.default_currency IS 'Default currency code, e.g. CNY, JPY, USD';
COMMENT ON COLUMN sys_tenant.default_theme IS 'Default UI theme: light, dark, or system';
COMMENT ON COLUMN sys_tenant.session_timeout_min IS 'Session timeout in minutes';
COMMENT ON COLUMN sys_tenant.mfa_policy IS 'MFA policy: disabled, optional, or required';
COMMENT ON COLUMN sys_tenant.allowed_login_methods IS 'JSON array of allowed login methods';
COMMENT ON COLUMN sys_tenant.password_policy IS 'JSON object defining password complexity rules';
COMMENT ON COLUMN sys_tenant.notification_channels IS 'JSON array of enabled notification channels';
COMMENT ON COLUMN sys_tenant.business_hours IS 'JSON object defining business hours and quiet periods';
COMMENT ON COLUMN sys_tenant.settings IS 'Extensible JSON settings for future configuration';
COMMENT ON COLUMN sys_tenant.logo_url IS 'Tenant logo URL for branding';
COMMENT ON COLUMN sys_tenant.primary_color IS 'Primary brand color hex code';
