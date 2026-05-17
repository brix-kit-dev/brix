-- ============================================================================
-- V007: Create sys_tenant_principal table
-- ============================================================================
-- Description: Tenant principal table for B2B2C Actor/Subject separation model.
--              Stores C-side (Subject) relationships: customers, guests, and
--              other external parties who consume services within a tenant.
--
--              This table is the Subject counterpart of sys_tenant_member (Actor).
--              Together they implement the Actor/Subject split defined in the
--              B2B2C multi-tenant model.
--
-- Architecture Layer: Layer 2C - Platform Commons (platform-tenant module)
--
-- Design Notes:
-- - Principal represents a Subject (C-side) relationship with a tenant
-- - One identity can be a principal in multiple tenants
-- - Each principal has its own type and lifecycle, independent of member
-- - Principal lifecycle is independent of any business object (Case/Order)
--
-- Relationship Model:
-- - sys_identity (1) --< sys_tenant_principal (N): One identity, many tenants
-- - sys_tenant (1) --< sys_tenant_principal (N): One tenant, many principals
-- - sys_tenant_principal vs sys_tenant_member: mutually exclusive role scopes
--   * Member  = Actor  (B-side): OWNER, ADMIN, MEMBER
--   * Principal = Subject (C-side): CUSTOMER, GUEST
--
-- Security Constraints:
-- - Principal tokens (pid) and member tokens (mid) are mutually exclusive
-- - Subject tokens MUST NOT access admin APIs
-- - Principal status must be ACTIVE to issue a valid Subject token
--
-- Referential Integrity:
-- - tenant_id references sys_tenant.id
-- - identity_id references sys_identity.id
-- - Unique constraint prevents duplicate principal records per tenant
--
-- @since 3.2.0
-- @see V003__create_sys_tenant_member.sql (Actor counterpart)
-- @see v1.2-多租户基础功能完整设计方案.md Section 10.3
-- @author Brix Platform Team
-- ============================================================================

-- Create sys_tenant_principal table
CREATE TABLE IF NOT EXISTS sys_tenant_principal (
    -- Primary key: Snowflake-generated unique identifier
    id              BIGINT          PRIMARY KEY,

    -- Foreign key: Reference to the tenant
    tenant_id       BIGINT          NOT NULL,

    -- Foreign key: Reference to the identity (user)
    identity_id     BIGINT          NOT NULL,

    -- Principal type within this tenant
    -- Valid values: CUSTOMER, GUEST
    -- @see io.brix.platform.tenant.enums.PrincipalType
    principal_type  VARCHAR(20)     NOT NULL,

    -- Principal status
    -- Valid values: ACTIVE, DISABLED, REVOKED
    -- @see io.brix.platform.tenant.enums.PrincipalStatus
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',

    -- Display name for this principal within the tenant context
    -- May differ from identity username (e.g., nickname in a clinic)
    display_name    VARCHAR(100),

    -- When the principal first joined this tenant
    joined_at       TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Last time the principal accessed this tenant
    -- Used for sorting in tenant selector (most recently accessed first)
    last_access_at  TIMESTAMPTZ,

    -- Audit timestamps
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ,

    -- Unique constraint: one principal per identity-tenant pair
    -- An identity cannot have duplicate principal records in the same tenant
    CONSTRAINT uk_principal_tenant_identity
        UNIQUE (tenant_id, identity_id),

    -- Foreign key: tenant must exist
    CONSTRAINT fk_principal_tenant
        FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)
        ON DELETE CASCADE,

    -- Foreign key: identity must exist
    CONSTRAINT fk_principal_identity
        FOREIGN KEY (identity_id) REFERENCES sys_identity(id)
        ON DELETE CASCADE,

    -- CHECK constraint: principal_type must be a valid Subject role
    -- Actor roles (OWNER/ADMIN/MEMBER) belong to sys_tenant_member
    CONSTRAINT chk_principal_type
        CHECK (principal_type IN ('CUSTOMER', 'GUEST'))
);

-- Index for tenant-based queries (list all principals of a tenant)
CREATE INDEX IF NOT EXISTS idx_principal_tenant
    ON sys_tenant_principal(tenant_id);

-- Index for identity-based queries (find all tenants where identity is a principal)
CREATE INDEX IF NOT EXISTS idx_principal_identity
    ON sys_tenant_principal(identity_id);

-- Index for status-based queries (find active principals)
CREATE INDEX IF NOT EXISTS idx_principal_status
    ON sys_tenant_principal(tenant_id, status);

-- Comments for documentation
COMMENT ON TABLE sys_tenant_principal IS 'B2B2C Subject table: stores C-side (customer/guest) relationships with tenants';
COMMENT ON COLUMN sys_tenant_principal.id IS 'Primary key (Snowflake ID)';
COMMENT ON COLUMN sys_tenant_principal.tenant_id IS 'Foreign key to sys_tenant';
COMMENT ON COLUMN sys_tenant_principal.identity_id IS 'Foreign key to sys_identity';
COMMENT ON COLUMN sys_tenant_principal.principal_type IS 'Subject role type (PrincipalType enum: CUSTOMER, GUEST)';
COMMENT ON COLUMN sys_tenant_principal.status IS 'Principal status (PrincipalStatus enum: ACTIVE, DISABLED, REVOKED)';
COMMENT ON COLUMN sys_tenant_principal.display_name IS 'Display name within tenant context';
COMMENT ON COLUMN sys_tenant_principal.joined_at IS 'When the principal first joined this tenant';
COMMENT ON COLUMN sys_tenant_principal.last_access_at IS 'Last access timestamp for tenant selector sorting';
COMMENT ON COLUMN sys_tenant_principal.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN sys_tenant_principal.updated_at IS 'Record last update timestamp';
