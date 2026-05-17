-- ============================================================================
-- V005: Create sys_organization table
-- ============================================================================
-- Description: Organizational structure within a tenant.
--              Supports hierarchical organization units (departments, teams).
--              This table HAS tenant_id as organizations are tenant-scoped.
--              
-- Architecture Layer: Layer 2C - Platform Commons (platform-tenant module)
-- 
-- Design Notes:
-- - Organizations provide hierarchical structure within a tenant
-- - Self-referential for parent-child relationships
-- - Used for permission inheritance and data scoping
-- - Can represent departments, teams, divisions, etc.
--
-- Hierarchy Example:
--   Company (root)
--   ├── Engineering
--   │   ├── Backend Team
--   │   └── Frontend Team
--   ├── Sales
--   │   ├── NA Region
--   │   └── EMEA Region
--   └── HR
--
-- Data Ownership:
-- - Business entities can be owned by organizations
-- - Allows data visibility based on org hierarchy
--
-- @since 3.1.0
-- @author Brix Platform Team
-- ============================================================================

-- Create sys_organization table
CREATE TABLE IF NOT EXISTS sys_organization (
    -- Primary key: Snowflake-generated unique identifier
    id              BIGINT          PRIMARY KEY,
    
    -- Tenant ID: Which tenant this organization belongs to
    -- Required for tenant isolation
    tenant_id       BIGINT          NOT NULL,
    
    -- Parent organization for hierarchy
    -- NULL for root-level organizations
    parent_id       BIGINT,
    
    -- Organization code: unique within tenant
    -- Used for API access and integrations
    code            VARCHAR(64)     NOT NULL,
    
    -- Display name
    name            VARCHAR(256)    NOT NULL,
    
    -- Organization description
    description     TEXT,
    
    -- Organization type (for categorization)
    -- e.g., 'DEPARTMENT', 'TEAM', 'DIVISION', 'REGION'
    org_type        VARCHAR(32),
    
    -- Sort order for display (within same parent)
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    
    -- Organization status
    -- Valid values: PENDING, ACTIVE, INACTIVE, SUSPENDED, DELETED
    -- @see io.brix.platform.tenant.enums.MemberStatus
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    
    -- Audit timestamps
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ,
    
    -- Foreign key constraint: tenant must exist
    CONSTRAINT fk_organization_tenant 
        FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) 
        ON DELETE CASCADE,
    
    -- Self-referential foreign key: parent must exist within same tenant
    CONSTRAINT fk_organization_parent 
        FOREIGN KEY (parent_id) REFERENCES sys_organization(id) 
        ON DELETE SET NULL,
    
    -- Unique constraint: code must be unique within tenant
    CONSTRAINT uk_sys_organization_tenant_code 
        UNIQUE (tenant_id, code)
);

-- Primary index for tenant-based queries (CRITICAL for tenant isolation)
CREATE INDEX IF NOT EXISTS idx_sys_organization_tenant 
    ON sys_organization(tenant_id);

-- Index for hierarchy traversal (find children of a parent)
CREATE INDEX IF NOT EXISTS idx_sys_organization_parent 
    ON sys_organization(parent_id);

-- Composite index for tenant + parent (common query pattern)
CREATE INDEX IF NOT EXISTS idx_sys_organization_tenant_parent 
    ON sys_organization(tenant_id, parent_id);

-- Index for status-based queries within tenant
CREATE INDEX IF NOT EXISTS idx_sys_organization_tenant_status 
    ON sys_organization(tenant_id, status);

-- Index for sorting within parent
CREATE INDEX IF NOT EXISTS idx_sys_organization_sort 
    ON sys_organization(tenant_id, parent_id, sort_order);

-- Comments for documentation
COMMENT ON TABLE sys_organization IS 'Hierarchical organization structure within a tenant';
COMMENT ON COLUMN sys_organization.id IS 'Primary key (Snowflake ID)';
COMMENT ON COLUMN sys_organization.tenant_id IS 'Foreign key to sys_tenant (for tenant isolation)';
COMMENT ON COLUMN sys_organization.parent_id IS 'Parent org ID (NULL for root orgs)';
COMMENT ON COLUMN sys_organization.code IS 'Unique code within tenant';
COMMENT ON COLUMN sys_organization.name IS 'Display name';
COMMENT ON COLUMN sys_organization.description IS 'Organization description';
COMMENT ON COLUMN sys_organization.org_type IS 'Organization type/category';
COMMENT ON COLUMN sys_organization.sort_order IS 'Display order within same parent';
COMMENT ON COLUMN sys_organization.status IS 'Organization status (MemberStatus enum)';
COMMENT ON COLUMN sys_organization.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN sys_organization.updated_at IS 'Last update timestamp';
