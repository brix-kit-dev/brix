-- ============================================================================
-- V004: Create sys_platform_admin table
-- ============================================================================
-- Description: Platform-level administrator accounts.
--              These are super users who manage the entire platform,
--              not individual tenants. System-level table.
--              
-- Architecture Layer: Layer 2C - Platform Commons (platform-tenant module)
-- 
-- Design Notes:
-- - Platform admins have cross-tenant access for platform management
-- - Different roles have different privilege levels
-- - All platform admin actions should be audit logged
-- - MFA should be enforced for all platform admin accounts
--
-- Role Hierarchy:
-- - SUPER_ADMIN: Full system access, infrastructure management
-- - PLATFORM_ADMIN: Tenant management, day-to-day operations
-- - SUPPORT_ADMIN: Customer support, limited access
-- - AUDITOR: Read-only compliance and monitoring
--
-- Security Requirements:
-- - Links to sys_identity for actual credentials
-- - Separate table allows fine-grained platform role control
-- - Status can be used for emergency lockout
--
-- @since 3.1.0
-- @author Brix Platform Team
-- ============================================================================

-- Create sys_platform_admin table
CREATE TABLE IF NOT EXISTS sys_platform_admin (
    -- Primary key: Snowflake-generated unique identifier
    id              BIGINT          PRIMARY KEY,
    
    -- Foreign key: Reference to the identity (user account)
    identity_id     BIGINT          NOT NULL,
    
    -- Platform admin role
    -- Valid values: SUPER_ADMIN, PLATFORM_ADMIN, SUPPORT_ADMIN, AUDITOR
    -- @see io.brix.platform.tenant.enums.PlatformAdminRole
    role            VARCHAR(32)     NOT NULL,
    
    -- Admin account status  
    -- Valid values: PENDING, ACTIVE, INACTIVE, SUSPENDED, DELETED
    -- @see io.brix.platform.tenant.enums.MemberStatus
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    
    -- Whether MFA is enabled (should be TRUE for all admins)
    mfa_enabled     BOOLEAN         NOT NULL DEFAULT FALSE,
    
    -- Notes about this admin (reason for access, etc.)
    notes           TEXT,
    
    -- Audit timestamps
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ,
    
    -- Foreign key constraint: identity must exist
    CONSTRAINT fk_platform_admin_identity 
        FOREIGN KEY (identity_id) REFERENCES sys_identity(id) 
        ON DELETE CASCADE,
    
    -- Unique constraint: one platform admin per identity
    -- An identity can only have one platform admin role
    CONSTRAINT uk_sys_platform_admin_identity 
        UNIQUE (identity_id)
);

-- Index for role-based queries (list all super admins, etc.)
CREATE INDEX IF NOT EXISTS idx_sys_platform_admin_role 
    ON sys_platform_admin(role);

-- Index for status-based queries
CREATE INDEX IF NOT EXISTS idx_sys_platform_admin_status 
    ON sys_platform_admin(status);

-- Index for MFA compliance monitoring
CREATE INDEX IF NOT EXISTS idx_sys_platform_admin_mfa 
    ON sys_platform_admin(mfa_enabled) 
    WHERE mfa_enabled = FALSE;

-- Comments for documentation
COMMENT ON TABLE sys_platform_admin IS 'Platform-level administrator accounts with cross-tenant access';
COMMENT ON COLUMN sys_platform_admin.id IS 'Primary key (Snowflake ID)';
COMMENT ON COLUMN sys_platform_admin.identity_id IS 'Foreign key to sys_identity';
COMMENT ON COLUMN sys_platform_admin.role IS 'Platform admin role (PlatformAdminRole enum)';
COMMENT ON COLUMN sys_platform_admin.status IS 'Admin account status (MemberStatus enum)';
COMMENT ON COLUMN sys_platform_admin.mfa_enabled IS 'Whether MFA is enabled (should be TRUE)';
COMMENT ON COLUMN sys_platform_admin.notes IS 'Administrative notes about this admin';
COMMENT ON COLUMN sys_platform_admin.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN sys_platform_admin.updated_at IS 'Last update timestamp';
