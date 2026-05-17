-- ============================================================================
-- V009: Create biz_user_profile with ref_type/ref_id polymorphic reference
-- ============================================================================
-- Description: Creates the biz_user_profile table with the B2B2C-compatible
--              polymorphic reference model (ref_type + ref_id) instead of the
--              originally planned member_id foreign key.
--
--              The ref_type/ref_id design allows a single profile to reference
--              either a sys_tenant_member (Actor) or sys_tenant_principal
--              (Subject), supporting both B-side and C-side users.
--
-- Architecture Layer: Layer 2C - Platform Commons (platform-tenant module)
--
-- Design Notes:
-- - This table was defined in v1.0 design but never migrated to the database.
--   Since there is no existing table and no legacy data, we create it with
--   the final target schema directly (no ALTER TABLE required).
-- - ref_type: 'MEMBER' or 'PRINCIPAL' (polymorphic discriminator)
-- - ref_id: the id from sys_tenant_member or sys_tenant_principal
-- - The old member_id column from v1.0 design is intentionally omitted
-- - Unique constraint (tenant_id, ref_type, ref_id) ensures one profile
--   per actor/subject per tenant
--
-- Reference Model:
--   ref_type='MEMBER'    + ref_id → sys_tenant_member.id    (Actor/B-side)
--   ref_type='PRINCIPAL' + ref_id → sys_tenant_principal.id (Subject/C-side)
--
-- @since 3.2.0
-- @see v1.2-多租户基础功能完整设计方案.md Section 10.5
-- @author Brix Platform Team
-- ============================================================================

-- Create biz_user_profile table with polymorphic reference
CREATE TABLE IF NOT EXISTS biz_user_profile (
    -- Primary key: Snowflake-generated unique identifier
    id              BIGINT          PRIMARY KEY,

    -- Foreign key: Reference to the tenant
    tenant_id       BIGINT          NOT NULL,

    -- Polymorphic reference type: which table does ref_id point to
    -- Valid values: MEMBER (sys_tenant_member), PRINCIPAL (sys_tenant_principal)
    -- @see io.brix.platform.tenant.enums.ProfileRefType
    ref_type        VARCHAR(20)     NOT NULL,

    -- Polymorphic reference id: the row id in the table indicated by ref_type
    ref_id          BIGINT          NOT NULL,

    -- User's preferred nickname within this tenant context
    nickname        VARCHAR(100),

    -- URL to user's avatar image
    avatar_url      VARCHAR(512),

    -- Display name (may differ from nickname, used in formal contexts)
    display_name    VARCHAR(100),

    -- User preferences as JSON object (theme, language, layout, etc.)
    preferences     JSONB           DEFAULT '{}'::jsonb,

    -- Extension data for custom fields (plugin-specific attributes)
    extended        JSONB           DEFAULT '{}'::jsonb,

    -- Audit timestamps
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ,

    -- Foreign key: tenant must exist
    CONSTRAINT fk_user_profile_tenant
        FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)
        ON DELETE CASCADE,

    -- Unique constraint: one profile per ref_type+ref_id per tenant
    CONSTRAINT uk_profile_tenant_ref
        UNIQUE (tenant_id, ref_type, ref_id),

    -- CHECK constraint: ref_type must be a valid discriminator
    CONSTRAINT chk_profile_ref_type
        CHECK (ref_type IN ('MEMBER', 'PRINCIPAL'))
);

-- Index for tenant-based profile queries
CREATE INDEX IF NOT EXISTS idx_user_profile_tenant
    ON biz_user_profile(tenant_id);

-- Index for polymorphic lookups (find profile by reference)
CREATE INDEX IF NOT EXISTS idx_user_profile_ref
    ON biz_user_profile(ref_type, ref_id);

-- Comments for documentation
COMMENT ON TABLE biz_user_profile IS 'User profile within a tenant context, with polymorphic reference to member or principal';
COMMENT ON COLUMN biz_user_profile.id IS 'Primary key (Snowflake ID)';
COMMENT ON COLUMN biz_user_profile.tenant_id IS 'Foreign key to sys_tenant';
COMMENT ON COLUMN biz_user_profile.ref_type IS 'Polymorphic discriminator: MEMBER or PRINCIPAL';
COMMENT ON COLUMN biz_user_profile.ref_id IS 'Foreign key to sys_tenant_member.id or sys_tenant_principal.id based on ref_type';
COMMENT ON COLUMN biz_user_profile.nickname IS 'User nickname within this tenant';
COMMENT ON COLUMN biz_user_profile.avatar_url IS 'URL to user avatar image';
COMMENT ON COLUMN biz_user_profile.display_name IS 'Formal display name within this tenant';
COMMENT ON COLUMN biz_user_profile.preferences IS 'User preferences JSON (theme, language, etc.)';
COMMENT ON COLUMN biz_user_profile.extended IS 'Extension JSON for plugin-specific custom attributes';
COMMENT ON COLUMN biz_user_profile.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN biz_user_profile.updated_at IS 'Record last update timestamp';
