-- ============================================================================
-- V003: Create sys_tenant_member table
-- ============================================================================
-- Description: Association table linking identities to tenants with roles.
--              Implements the many-to-many relationship between sys_identity
--              and sys_tenant with additional membership attributes.
--              This is a system-level table (no tenant_id column on itself).
--              
-- Architecture Layer: Layer 2C - Platform Commons (platform-tenant module)
-- 
-- Design Notes:
-- - Each row represents one identity's membership in one tenant
-- - An identity can be a member of multiple tenants with different roles
-- - member_type determines the privilege level within the tenant
-- - Each tenant MUST keep at least one active OWNER
--
-- Membership Model:
-- - OWNER: Full control, can delete tenant (at least one active owner per tenant)
-- - ADMIN: Can manage members and settings
-- - MEMBER: Standard access based on permissions
-- - Subject roles are stored in sys_tenant_principal
--
-- Referential Integrity:
-- - tenant_id references sys_tenant.id
-- - identity_id references sys_identity.id
-- - Unique constraint prevents duplicate memberships
--
-- @since 3.1.0
-- @author Brix Platform Team
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Create sys_tenant_member table
CREATE TABLE IF NOT EXISTS sys_tenant_member (
    -- Primary key: Snowflake-generated unique identifier
    id              BIGINT          PRIMARY KEY,
    
    -- Foreign key: Reference to the tenant
    tenant_id       BIGINT          NOT NULL,
    
    -- Foreign key: Reference to the identity (user)
    identity_id     BIGINT          NOT NULL,

    -- Stable Actor context identifier. This value is used as the JWT cid.
    context_id      UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- Authorization version snapshot used by Actor tokens as mver.
    authz_version   INTEGER         NOT NULL DEFAULT 1,
    
    -- Member type/role within this tenant
    -- Valid values: OWNER, ADMIN, MEMBER
    -- @see io.brix.platform.tenant.enums.TenantMemberType
    member_type     VARCHAR(32)     NOT NULL DEFAULT 'MEMBER',
    
    -- Membership status
    -- Valid values: PENDING, ACTIVE, INACTIVE, SUSPENDED, DELETED
    -- @see io.brix.platform.tenant.enums.MemberStatus
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    
    -- When the member joined this tenant
    joined_at       TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Audit timestamps
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ,
    
    -- Foreign key constraint: tenant must exist
    CONSTRAINT fk_tenant_member_tenant 
        FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) 
        ON DELETE CASCADE,
    
    -- Foreign key constraint: identity must exist
    CONSTRAINT fk_tenant_member_identity 
        FOREIGN KEY (identity_id) REFERENCES sys_identity(id) 
        ON DELETE CASCADE,
    
    -- Unique constraint: one membership per identity-tenant pair
    -- Prevents duplicate memberships
    CONSTRAINT uk_sys_tenant_member_tenant_identity 
        UNIQUE (tenant_id, identity_id),

    -- Stable context identifiers are globally unique.
    CONSTRAINT uk_member_context_id
        UNIQUE (context_id),

    -- Composite unique key used by biz_user_profile composite foreign keys.
    CONSTRAINT uk_member_tenant_id
        UNIQUE (tenant_id, id)
);

-- Index for tenant-based queries (list all members of a tenant)
CREATE INDEX IF NOT EXISTS idx_sys_tenant_member_tenant 
    ON sys_tenant_member(tenant_id);

-- Index for identity-based queries (find all tenants for a user)
CREATE INDEX IF NOT EXISTS idx_sys_tenant_member_identity 
    ON sys_tenant_member(identity_id);

-- Index for member type queries (find all admins, etc.)
CREATE INDEX IF NOT EXISTS idx_sys_tenant_member_type 
    ON sys_tenant_member(tenant_id, member_type);

-- Index for status-based queries
CREATE INDEX IF NOT EXISTS idx_sys_tenant_member_status 
    ON sys_tenant_member(tenant_id, status);

-- Comments for documentation
COMMENT ON TABLE sys_tenant_member IS 'Association table for identity-tenant membership with roles';
COMMENT ON COLUMN sys_tenant_member.id IS 'Primary key (Snowflake ID)';
COMMENT ON COLUMN sys_tenant_member.tenant_id IS 'Foreign key to sys_tenant';
COMMENT ON COLUMN sys_tenant_member.identity_id IS 'Foreign key to sys_identity';
COMMENT ON COLUMN sys_tenant_member.context_id IS 'Stable Actor context identifier used as JWT cid';
COMMENT ON COLUMN sys_tenant_member.authz_version IS 'Authorization version used as Actor token mver snapshot';
COMMENT ON COLUMN sys_tenant_member.member_type IS 'Role within tenant (TenantMemberType enum)';
COMMENT ON COLUMN sys_tenant_member.status IS 'Membership status (MemberStatus enum)';
COMMENT ON COLUMN sys_tenant_member.joined_at IS 'When the member joined this tenant';
COMMENT ON COLUMN sys_tenant_member.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN sys_tenant_member.updated_at IS 'Last update timestamp';
