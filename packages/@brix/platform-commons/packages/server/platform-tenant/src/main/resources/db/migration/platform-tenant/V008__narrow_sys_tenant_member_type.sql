-- ============================================================================
-- V008: Narrow sys_tenant_member to Actor-only roles
-- ============================================================================
-- Description: Removes CUSTOMER and GUEST from sys_tenant_member as part of
--              the B2B2C Actor/Subject separation. These roles now belong in
--              sys_tenant_principal (created in V007).
--
--              After this migration, sys_tenant_member exclusively represents
--              B-side (Actor) roles: OWNER, ADMIN, MEMBER.
--
-- Architecture Layer: Layer 2C - Platform Commons (platform-tenant module)
--
-- Migration Steps:
-- 1. Migrate any existing CUSTOMER/GUEST rows to sys_tenant_principal
-- 2. Delete migrated rows from sys_tenant_member
-- 3. Add CHECK constraint to enforce Actor-only member types
--
-- Design Notes:
-- - ON CONFLICT DO NOTHING ensures idempotent migration
-- - sys_tenant_member does not have display_name or last_access_at columns,
--   so those are set to NULL in the target principal table
-- - CHECK constraint name "chk_member_type" aligns with design document
--
-- Rollback: Drop constraint, re-insert from principal table filtered by
--           former member records (requires separate rollback script)
--
-- @since 3.2.0
-- @see V003__create_sys_tenant_member.sql (original table)
-- @see V007__create_sys_tenant_principal.sql (target table)
-- @see v1.2-多租户基础功能完整设计方案.md Section 10.4
-- @author Brix Platform Team
-- ============================================================================

-- Step 1: Migrate CUSTOMER/GUEST rows to sys_tenant_principal (if any exist)
-- Note: sys_tenant_member does not have display_name or last_access_at,
-- so those default to NULL in the target principal table.
INSERT INTO sys_tenant_principal (
    id, tenant_id, identity_id, principal_type, status,
    display_name, joined_at, last_access_at, created_at, updated_at
)
SELECT
    id, tenant_id, identity_id,
    CASE WHEN member_type = 'CUSTOMER' THEN 'CUSTOMER' ELSE 'GUEST' END,
    status,
    NULL,                -- display_name: not present in sys_tenant_member
    joined_at,
    NULL,                -- last_access_at: not present in sys_tenant_member
    created_at,
    updated_at
FROM sys_tenant_member
WHERE member_type IN ('CUSTOMER', 'GUEST')
ON CONFLICT DO NOTHING;

-- Step 2: Delete migrated CUSTOMER/GUEST records from sys_tenant_member
DELETE FROM sys_tenant_member
WHERE member_type IN ('CUSTOMER', 'GUEST');

-- Step 3: Add CHECK constraint to restrict member_type to Actor roles only
-- The constraint name "chk_member_type" is referenced in design documentation
ALTER TABLE sys_tenant_member
    DROP CONSTRAINT IF EXISTS chk_member_type;

ALTER TABLE sys_tenant_member
    ADD CONSTRAINT chk_member_type
    CHECK (member_type IN ('OWNER', 'ADMIN', 'MEMBER'));

-- Update table comment to reflect the narrowed scope
COMMENT ON COLUMN sys_tenant_member.member_type IS
    'Actor role within tenant (TenantMemberType enum: OWNER, ADMIN, MEMBER). Subject roles (CUSTOMER, GUEST) are in sys_tenant_principal.';
