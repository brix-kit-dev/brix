-- ============================================================================
-- V006: Create biz_audit_log table
-- ============================================================================
-- Description: Audit log table for tracking all sensitive operations.
--              Business table with full tenant isolation and ownership model.
--              
-- Architecture Layer: Layer 2C - Platform Commons (platform-tenant module)
-- 
-- Design Notes:
-- - Records all sensitive operations for compliance and security
-- - Includes full context: who, what, when, where
-- - Supports both tenant-scoped and system-level audits
-- - Immutable records (no UPDATE allowed in application code)
--
-- Audit Event Categories:
-- - AUTHENTICATION: Login, logout, password change
-- - AUTHORIZATION: Permission changes, role assignments
-- - DATA_ACCESS: Read sensitive data
-- - DATA_MUTATION: Create, update, delete operations
-- - CONFIGURATION: System/tenant configuration changes
-- - SECURITY: Security events, violations
--
-- Ownership Model:
-- - tenant_id: Which tenant this audit belongs to (NULL for system events)
-- - owner_member_id: The tenant member who performed the action
-- - owner_org_id: The organization context of the action
-- - created_by: Identity ID of the actor
--
-- @since 3.1.0
-- @author Brix Platform Team
-- ============================================================================

-- Create biz_audit_log table
CREATE TABLE IF NOT EXISTS biz_audit_log (
    -- Primary key: Snowflake-generated unique identifier
    id                  BIGINT          PRIMARY KEY,
    
    -- ========================================================================
    -- Tenant Isolation & Ownership Fields
    -- ========================================================================
    
    -- Tenant ID: Which tenant this audit belongs to
    -- NULL for system-level events (platform admin actions)
    tenant_id           BIGINT,
    
    -- Owner member ID: The tenant member who performed the action
    -- Links to sys_tenant_member.id
    owner_member_id     BIGINT,
    
    -- Owner organization ID: The org context of the action
    -- Links to sys_organization.id
    owner_org_id        BIGINT,
    
    -- Created by: The identity who performed the action
    -- Links to sys_identity.id (always required)
    created_by          BIGINT          NOT NULL,
    
    -- ========================================================================
    -- Audit Event Information
    -- ========================================================================
    
    -- Action performed
    -- e.g., 'LOGIN', 'LOGOUT', 'CREATE', 'UPDATE', 'DELETE', 'VIEW'
    action              VARCHAR(64)     NOT NULL,
    
    -- Resource type being acted upon
    -- e.g., 'TENANT', 'USER', 'CASE', 'CONTRACT', 'PERMISSION'
    resource_type       VARCHAR(64)     NOT NULL,
    
    -- Resource ID (the specific entity affected)
    resource_id         VARCHAR(128),
    
    -- Human-readable description of the action
    description         TEXT,
    
    -- ========================================================================
    -- Context Information
    -- ========================================================================
    
    -- Client IP address
    client_ip           VARCHAR(45),
    
    -- User agent string
    user_agent          VARCHAR(512),
    
    -- Request ID for correlation with other logs
    request_id          VARCHAR(128),
    
    -- Additional context as JSON
    -- Stores before/after values, related IDs, etc.
    context             JSONB,
    
    -- ========================================================================
    -- Result Information
    -- ========================================================================
    
    -- Whether the action was successful
    success             BOOLEAN         NOT NULL DEFAULT TRUE,
    
    -- Error message if action failed
    error_message       TEXT,
    
    -- Error code if action failed
    error_code          VARCHAR(64),
    
    -- ========================================================================
    -- Timestamps
    -- ========================================================================
    
    -- When the action occurred (immutable)
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraint: tenant must exist (optional for system events)
    CONSTRAINT fk_audit_log_tenant 
        FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) 
        ON DELETE SET NULL
);

-- ============================================================================
-- Indexes for Audit Log Queries
-- ============================================================================

-- Primary index for tenant-based queries (CRITICAL for tenant isolation)
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_tenant 
    ON biz_audit_log(tenant_id);

-- Index for resource-based queries (find all logs for a specific entity)
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_resource 
    ON biz_audit_log(resource_type, resource_id);

-- Index for user-based queries (find all logs for a specific user)
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_created_by 
    ON biz_audit_log(created_by);

-- Index for time-based queries (recent logs, time range filtering)
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_created_at 
    ON biz_audit_log(created_at DESC);

-- Composite index for tenant + time (common query pattern)
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_tenant_time 
    ON biz_audit_log(tenant_id, created_at DESC);

-- Index for action-based queries (find all login events, etc.)
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_action 
    ON biz_audit_log(tenant_id, action);

-- Index for request correlation
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_request_id 
    ON biz_audit_log(request_id) 
    WHERE request_id IS NOT NULL;

-- Index for failed operations monitoring
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_failures 
    ON biz_audit_log(tenant_id, created_at) 
    WHERE success = FALSE;

-- Index for ownership-based queries
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_owner_member 
    ON biz_audit_log(owner_member_id);

CREATE INDEX IF NOT EXISTS idx_biz_audit_log_owner_org 
    ON biz_audit_log(owner_org_id);

-- ============================================================================
-- Comments
-- ============================================================================

COMMENT ON TABLE biz_audit_log IS 'Audit log for tracking all sensitive operations';
COMMENT ON COLUMN biz_audit_log.id IS 'Primary key (Snowflake ID)';
COMMENT ON COLUMN biz_audit_log.tenant_id IS 'Tenant ID (NULL for system events)';
COMMENT ON COLUMN biz_audit_log.owner_member_id IS 'Tenant member who performed action';
COMMENT ON COLUMN biz_audit_log.owner_org_id IS 'Organization context of action';
COMMENT ON COLUMN biz_audit_log.created_by IS 'Identity ID of the actor';
COMMENT ON COLUMN biz_audit_log.action IS 'Action type (LOGIN, CREATE, UPDATE, etc.)';
COMMENT ON COLUMN biz_audit_log.resource_type IS 'Type of resource acted upon';
COMMENT ON COLUMN biz_audit_log.resource_id IS 'ID of the resource';
COMMENT ON COLUMN biz_audit_log.description IS 'Human-readable description';
COMMENT ON COLUMN biz_audit_log.client_ip IS 'Client IP address';
COMMENT ON COLUMN biz_audit_log.user_agent IS 'Client user agent';
COMMENT ON COLUMN biz_audit_log.request_id IS 'Request correlation ID';
COMMENT ON COLUMN biz_audit_log.context IS 'Additional context (JSON)';
COMMENT ON COLUMN biz_audit_log.success IS 'Whether action succeeded';
COMMENT ON COLUMN biz_audit_log.error_message IS 'Error message if failed';
COMMENT ON COLUMN biz_audit_log.error_code IS 'Error code if failed';
COMMENT ON COLUMN biz_audit_log.created_at IS 'When the action occurred';
