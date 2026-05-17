-- ============================================================================
-- V012: Add Tenant Quota Fields
-- ============================================================================
-- Phase 4, Task #30: Basic Quota Validation
--
-- Design Reference: v1.2-multi-tenant-design Section 12.4 Task #30
-- Architecture Layer: Infrastructure (Flyway Migration)
--
-- Purpose:
--   Add max_users and max_principals hard-limit columns to sys_tenant.
--   These columns define the maximum number of B-side members (Actor)
--   and C-side principals (Subject) allowed per tenant.
--
--   Quota enforcement is handled by TenantQuotaService, which checks
--   these limits before admitting new members/principals.
--
-- Default Values:
--   - max_users = 0 (unlimited, no quota enforcement)
--   - max_principals = 0 (unlimited, no quota enforcement)
--   A value of 0 means quota is not enforced for that dimension.
-- ============================================================================

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS max_users
    INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN sys_tenant.max_users IS 'Maximum B-side members (Actor). 0 = unlimited.';

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS max_principals
    INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN sys_tenant.max_principals IS 'Maximum C-side principals (Subject). 0 = unlimited.';
