-- ============================================================================
-- V002: Create sys_identity table
-- ============================================================================
-- Description: Global identity table for user accounts.
--              An identity is a unique user that can belong to multiple tenants.
--              This is a system-level table (no tenant_id column).
--              
-- Architecture Layer: Layer 2C - Platform Commons (platform-tenant module)
-- 
-- Design Notes:
-- - Identity represents a unique user across the entire platform
-- - One identity can be a member of multiple tenants (via sys_tenant_member)
-- - Email is globally unique for identity lookup and authentication
-- - Password hash stored using bcrypt or similar secure algorithm
--
-- Multi-Tenancy Model:
-- - sys_identity: Global user account (one per person)
-- - sys_tenant_member: Many-to-many relationship (identity <-> tenant)
-- - This allows users to access multiple tenants with one account
--
-- Security Considerations:
-- - Password stored as hash (never plain text)
-- - Email uniqueness prevents duplicate accounts
-- - Status can be used for account locking/suspension
--
-- @since 3.1.0
-- @author Brix Platform Team
-- ============================================================================

-- Create sys_identity table
CREATE TABLE IF NOT EXISTS sys_identity (
    -- Primary key: Snowflake-generated unique identifier
    id              BIGINT          PRIMARY KEY,
    
    -- Username for display (not unique, users can have same display name)
    username        VARCHAR(128)    NOT NULL,
    
    -- Email address: unique identifier for authentication
    -- Used for login, password reset, and notifications
    email           VARCHAR(256)    NOT NULL,
    
    -- Password hash: bcrypt or Argon2 encoded
    -- NULL for OAuth-only accounts
    password_hash   VARCHAR(256),
    
    -- Account status
    -- Valid values: PENDING, ACTIVE, INACTIVE, SUSPENDED, DELETED
    -- @see io.brix.platform.tenant.enums.MemberStatus
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    
    -- Email verification status
    email_verified  BOOLEAN         NOT NULL DEFAULT FALSE,
    
    -- Last login timestamp for security auditing
    last_login_at   TIMESTAMPTZ,
    
    -- Audit timestamps
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ,
    
    -- Unique constraint on email (global uniqueness)
    -- Critical for authentication and preventing duplicate accounts
    CONSTRAINT uk_sys_identity_email UNIQUE (email)
);

-- Index for status-based queries (active users, suspended accounts)
CREATE INDEX IF NOT EXISTS idx_sys_identity_status 
    ON sys_identity(status);

-- Index for email verification filtering
CREATE INDEX IF NOT EXISTS idx_sys_identity_email_verified 
    ON sys_identity(email_verified) 
    WHERE email_verified = FALSE;

-- Index for last login tracking (security monitoring)
CREATE INDEX IF NOT EXISTS idx_sys_identity_last_login 
    ON sys_identity(last_login_at DESC NULLS LAST);

-- Comments for documentation
COMMENT ON TABLE sys_identity IS 'Global identity table. One identity can belong to multiple tenants.';
COMMENT ON COLUMN sys_identity.id IS 'Primary key (Snowflake ID)';
COMMENT ON COLUMN sys_identity.username IS 'Display name for the user';
COMMENT ON COLUMN sys_identity.email IS 'Unique email address for authentication';
COMMENT ON COLUMN sys_identity.password_hash IS 'Bcrypt/Argon2 password hash (NULL for OAuth accounts)';
COMMENT ON COLUMN sys_identity.status IS 'Account status (MemberStatus enum)';
COMMENT ON COLUMN sys_identity.email_verified IS 'Whether email has been verified';
COMMENT ON COLUMN sys_identity.last_login_at IS 'Last successful login timestamp';
COMMENT ON COLUMN sys_identity.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN sys_identity.updated_at IS 'Last update timestamp';
