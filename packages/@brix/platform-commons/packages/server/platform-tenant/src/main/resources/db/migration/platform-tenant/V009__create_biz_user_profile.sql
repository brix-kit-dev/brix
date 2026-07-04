-- ============================================================================
-- V009: Create biz_user_profile with strong Actor/Subject references
-- ============================================================================
-- Description: Creates the biz_user_profile table with the B2B2C-compatible
--              dual nullable reference model. Each profile references either
--              a sys_tenant_member (Actor) or a sys_tenant_principal (Subject),
--              and the database verifies that the referenced context belongs
--              to the same tenant.
--
-- Architecture Layer: Layer 2C - Platform Commons (platform-tenant module)
--
-- Design Notes:
-- - member_id references sys_tenant_member for Actor profiles.
-- - principal_id references sys_tenant_principal for Subject profiles.
-- - Exactly one of member_id/principal_id must be non-null.
-- - Composite foreign keys guarantee tenant_id consistency.
--
-- Reference Model:
--   member_id    -> sys_tenant_member.id    (Actor/B-side)
--   principal_id -> sys_tenant_principal.id (Subject/C-side)
--
-- @since 3.2.0
-- @see v1.2-多租户基础功能完整设计方案.md Section 10.5
-- @author Brix Platform Team
-- ============================================================================

-- Create biz_user_profile table with strong references
CREATE TABLE IF NOT EXISTS biz_user_profile (
    -- Primary key: Snowflake-generated unique identifier
    id              BIGINT          PRIMARY KEY,

    -- Foreign key: Reference to the tenant
    tenant_id       BIGINT          NOT NULL,

    -- Actor profile reference. Exactly one of member_id/principal_id is non-null.
    member_id       BIGINT,

    -- Subject profile reference. Exactly one of member_id/principal_id is non-null.
    principal_id    BIGINT,

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

    -- Composite foreign keys guarantee that the referenced context belongs to this tenant.
    CONSTRAINT fk_profile_member
        FOREIGN KEY (tenant_id, member_id) REFERENCES sys_tenant_member(tenant_id, id),

    CONSTRAINT fk_profile_principal
        FOREIGN KEY (tenant_id, principal_id) REFERENCES sys_tenant_principal(tenant_id, id),

    -- One profile per Actor/Subject context in a tenant.
    CONSTRAINT uk_profile_member
        UNIQUE (tenant_id, member_id),

    CONSTRAINT uk_profile_principal
        UNIQUE (tenant_id, principal_id),

    -- Exactly one reference must be present.
    CONSTRAINT chk_profile_ref_exclusive CHECK (
        (member_id IS NOT NULL AND principal_id IS NULL)
     OR (member_id IS NULL     AND principal_id IS NOT NULL)
    )
);

-- Index for tenant-based profile queries
CREATE INDEX IF NOT EXISTS idx_user_profile_tenant
    ON biz_user_profile(tenant_id);

-- Indexes for context lookups
CREATE INDEX IF NOT EXISTS idx_user_profile_member
    ON biz_user_profile(tenant_id, member_id);

CREATE INDEX IF NOT EXISTS idx_user_profile_principal
    ON biz_user_profile(tenant_id, principal_id);

-- Comments for documentation
COMMENT ON TABLE biz_user_profile IS 'User profile within a tenant context, with strong reference to member or principal';
COMMENT ON COLUMN biz_user_profile.id IS 'Primary key (Snowflake ID)';
COMMENT ON COLUMN biz_user_profile.tenant_id IS 'Foreign key to sys_tenant';
COMMENT ON COLUMN biz_user_profile.member_id IS 'Actor profile reference to sys_tenant_member.id';
COMMENT ON COLUMN biz_user_profile.principal_id IS 'Subject profile reference to sys_tenant_principal.id';
COMMENT ON COLUMN biz_user_profile.nickname IS 'User nickname within this tenant';
COMMENT ON COLUMN biz_user_profile.avatar_url IS 'URL to user avatar image';
COMMENT ON COLUMN biz_user_profile.display_name IS 'Formal display name within this tenant';
COMMENT ON COLUMN biz_user_profile.preferences IS 'User preferences JSON (theme, language, etc.)';
COMMENT ON COLUMN biz_user_profile.extended IS 'Extension JSON for plugin-specific custom attributes';
COMMENT ON COLUMN biz_user_profile.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN biz_user_profile.updated_at IS 'Record last update timestamp';
