-- ============================================================================
-- V021: Phase 3 FIRST_OWNER and TenantAdministration internal contract
-- ============================================================================
-- Owner: platform-tenant
-- This is a forward-only migration after V020. It must not modify historical
-- migration files or silently discard conflicting invitation data.
-- ============================================================================

ALTER TABLE sys_tenant_invitation
    ADD COLUMN IF NOT EXISTS invitation_purpose VARCHAR(32),
    ADD COLUMN IF NOT EXISTS inviter_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS platform_admin_id BIGINT;

UPDATE sys_tenant_invitation
SET invitation_purpose = 'TENANT_MEMBER'
WHERE invitation_purpose IS NULL;

UPDATE sys_tenant_invitation
SET inviter_type = 'TENANT_MEMBER'
WHERE inviter_type IS NULL;

DO $$
DECLARE
    conflict_report JSONB;
BEGIN
    SELECT jsonb_agg(row_to_json(conflicts))
    INTO conflict_report
    FROM (
        SELECT id, tenant_id, invitation_purpose, inviter_type, target_type, target_role,
               platform_admin_id, invited_by_member_id
        FROM sys_tenant_invitation
        WHERE invitation_purpose NOT IN ('FIRST_OWNER', 'TENANT_MEMBER')
           OR inviter_type NOT IN ('PLATFORM_ADMIN', 'TENANT_MEMBER')
           OR (inviter_type = 'PLATFORM_ADMIN' AND (platform_admin_id IS NULL OR invited_by_member_id IS NOT NULL))
           OR (inviter_type = 'TENANT_MEMBER' AND (invited_by_member_id IS NULL OR platform_admin_id IS NOT NULL))
           OR (invitation_purpose = 'FIRST_OWNER'
               AND (target_type <> 'MEMBER' OR target_role <> 'OWNER' OR inviter_type <> 'PLATFORM_ADMIN'))
    ) conflicts;

    IF conflict_report IS NOT NULL THEN
        RAISE EXCEPTION 'V021 cannot apply FIRST_OWNER invitation constraints because conflicts exist: %',
            conflict_report;
    END IF;
END;
$$;

ALTER TABLE sys_tenant_invitation
    ALTER COLUMN invitation_purpose SET NOT NULL,
    ALTER COLUMN inviter_type SET NOT NULL;

ALTER TABLE sys_tenant_invitation DROP CONSTRAINT IF EXISTS chk_invite_purpose;
ALTER TABLE sys_tenant_invitation ADD CONSTRAINT chk_invite_purpose
    CHECK (invitation_purpose IN ('FIRST_OWNER', 'TENANT_MEMBER'));

ALTER TABLE sys_tenant_invitation DROP CONSTRAINT IF EXISTS chk_invite_inviter_type;
ALTER TABLE sys_tenant_invitation ADD CONSTRAINT chk_invite_inviter_type
    CHECK (inviter_type IN ('PLATFORM_ADMIN', 'TENANT_MEMBER'));

ALTER TABLE sys_tenant_invitation DROP CONSTRAINT IF EXISTS chk_invite_inviter_scope;
ALTER TABLE sys_tenant_invitation ADD CONSTRAINT chk_invite_inviter_scope CHECK (
    (inviter_type = 'PLATFORM_ADMIN' AND platform_admin_id IS NOT NULL AND invited_by_member_id IS NULL)
 OR (inviter_type = 'TENANT_MEMBER' AND invited_by_member_id IS NOT NULL AND platform_admin_id IS NULL)
);

ALTER TABLE sys_tenant_invitation DROP CONSTRAINT IF EXISTS chk_invite_first_owner_shape;
ALTER TABLE sys_tenant_invitation ADD CONSTRAINT chk_invite_first_owner_shape CHECK (
    invitation_purpose <> 'FIRST_OWNER'
 OR (target_type = 'MEMBER' AND target_role = 'OWNER' AND inviter_type = 'PLATFORM_ADMIN')
);

ALTER TABLE sys_tenant_invitation DROP CONSTRAINT IF EXISTS chk_invite_status_time;
ALTER TABLE sys_tenant_invitation ADD CONSTRAINT chk_invite_status_time CHECK (
    (status = 'PENDING' AND accepted_at IS NULL AND revoked_at IS NULL)
 OR (status = 'ACCEPTED' AND accepted_at IS NOT NULL AND revoked_at IS NULL)
 OR (status = 'REVOKED' AND revoked_at IS NOT NULL AND accepted_at IS NULL)
 OR (status = 'EXPIRED' AND accepted_at IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_invite_purpose_status
    ON sys_tenant_invitation(tenant_id, invitation_purpose, status);

CREATE TABLE IF NOT EXISTS platform_tenant_outbox (
    event_id       VARCHAR(64)  PRIMARY KEY,
    event_type     VARCHAR(128) NOT NULL,
    schema_version VARCHAR(16)  NOT NULL,
    tenant_id      BIGINT,
    partition_key  VARCHAR(128) NOT NULL,
    payload        JSONB        NOT NULL,
    reliability    VARCHAR(20)  NOT NULL DEFAULT 'CRITICAL',
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ,
    CONSTRAINT chk_platform_tenant_outbox_reliability
        CHECK (reliability IN ('CRITICAL', 'STANDARD', 'BEST_EFFORT')),
    CONSTRAINT chk_platform_tenant_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'PARKED'))
);

CREATE INDEX IF NOT EXISTS idx_platform_tenant_outbox_pending
    ON platform_tenant_outbox(status, occurred_at);

CREATE TABLE IF NOT EXISTS platform_tenant_inbox (
    handler_id   VARCHAR(128) NOT NULL,
    message_id   VARCHAR(64)  NOT NULL,
    message_type VARCHAR(128) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PROCESSED',
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (handler_id, message_id),
    CONSTRAINT chk_platform_tenant_inbox_status
        CHECK (status IN ('PROCESSED', 'FAILED'))
);

COMMENT ON COLUMN sys_tenant_invitation.invitation_purpose IS
    'FIRST_OWNER activates a pending tenant; TENANT_MEMBER is ordinary tenant invitation';
COMMENT ON COLUMN sys_tenant_invitation.platform_admin_id IS
    'Platform identity that created a platform-scoped FIRST_OWNER invitation';
COMMENT ON TABLE platform_tenant_outbox IS
    'Canonical outbox owned by platform-tenant for critical tenant facts';
COMMENT ON TABLE platform_tenant_inbox IS
    'Canonical inbox owned by platform-tenant for idempotent inbound messages';
