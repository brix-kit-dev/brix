-- =============================================================================
-- V026: Phase 3 clean FIRST_OWNER invariants
--
-- Owner: platform-tenant
-- Runtime Shell 3.0.10 / multi-tenant 4.0 require platform-tenant to own the
-- tenant directory, invitation, quota, tenant audit, and canonical outbox rows
-- without cross-Owner identity foreign keys or platform-admin repository access.
-- =============================================================================

-- Platform operator identity is an opaque audit reference from the verified
-- Invocation Context. It is not a foreign key to the identity Owner.
ALTER TABLE sys_tenant_invitation
    ADD COLUMN IF NOT EXISTS platform_operator_ref VARCHAR(128);

UPDATE sys_tenant_invitation
   SET platform_operator_ref = 'platform-identity:' || platform_admin_id::text
 WHERE platform_operator_ref IS NULL
   AND platform_admin_id IS NOT NULL;

DO $$
DECLARE
    conflict_report JSONB;
BEGIN
    SELECT jsonb_agg(row_to_json(conflicts))
      INTO conflict_report
      FROM (
        SELECT id, tenant_id, invitation_purpose, inviter_type, platform_operator_ref
          FROM sys_tenant_invitation
         WHERE inviter_type = 'PLATFORM_ADMIN'
           AND (platform_operator_ref IS NULL OR btrim(platform_operator_ref) = '')
      ) conflicts;

    IF conflict_report IS NOT NULL THEN
        RAISE EXCEPTION 'V026 cannot enforce opaque platform operator refs because conflicts exist: %',
            conflict_report;
    END IF;
END;
$$;

ALTER TABLE sys_tenant_invitation DROP CONSTRAINT IF EXISTS chk_invite_inviter_scope;
ALTER TABLE sys_tenant_invitation ADD CONSTRAINT chk_invite_inviter_scope CHECK (
    (inviter_type = 'PLATFORM_ADMIN'
        AND platform_operator_ref IS NOT NULL
        AND btrim(platform_operator_ref) <> ''
        AND invited_by_member_id IS NULL)
 OR (inviter_type = 'TENANT_MEMBER'
        AND invited_by_member_id IS NOT NULL
        AND platform_operator_ref IS NULL)
);

-- Identity references are opaque ids validated through the identity Owner
-- contract. They must not be enforced by platform-tenant DDL.
ALTER TABLE sys_tenant_member
    DROP CONSTRAINT IF EXISTS fk_tenant_member_identity;
ALTER TABLE sys_tenant_principal
    DROP CONSTRAINT IF EXISTS fk_principal_identity;

DO $$
DECLARE
    conflict_report JSONB;
BEGIN
    SELECT jsonb_agg(row_to_json(conflicts))
      INTO conflict_report
      FROM (
        SELECT id, tenant_id, member_type, status
          FROM sys_tenant_member
         WHERE member_type NOT IN ('OWNER', 'ADMIN', 'MEMBER')
            OR status NOT IN ('ACTIVE', 'INVITED', 'DISABLED', 'REVOKED')
      ) conflicts;

    IF conflict_report IS NOT NULL THEN
        RAISE EXCEPTION 'V026 cannot enforce Actor-only member constraints because conflicts exist: %',
            conflict_report;
    END IF;
END;
$$;

ALTER TABLE sys_tenant_member DROP CONSTRAINT IF EXISTS chk_member_type;
ALTER TABLE sys_tenant_member ADD CONSTRAINT chk_member_type
    CHECK (member_type IN ('OWNER', 'ADMIN', 'MEMBER'));

ALTER TABLE sys_tenant_member DROP CONSTRAINT IF EXISTS chk_member_status;
ALTER TABLE sys_tenant_member ADD CONSTRAINT chk_member_status
    CHECK (status IN ('ACTIVE', 'INVITED', 'DISABLED', 'REVOKED'));

ALTER TABLE sys_installation_quota DROP CONSTRAINT IF EXISTS chk_installation_quota_non_negative;
ALTER TABLE sys_installation_quota ADD CONSTRAINT chk_installation_quota_non_negative
    CHECK (quota >= 0 AND used >= 0);

ALTER TABLE sys_installation_quota DROP CONSTRAINT IF EXISTS chk_installation_quota_used_lte_quota;
ALTER TABLE sys_installation_quota ADD CONSTRAINT chk_installation_quota_used_lte_quota
    CHECK (used <= quota);

CREATE OR REPLACE FUNCTION guard_active_tenant_has_owner() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IN ('ACTIVE', 'TRIAL') THEN
        IF NOT EXISTS (
            SELECT 1
              FROM sys_tenant_member
             WHERE tenant_id = NEW.id
               AND member_type = 'OWNER'
               AND status = 'ACTIVE'
        ) THEN
            RAISE EXCEPTION 'TENANT_ACTIVE_OWNER_REQUIRED: tenant % cannot be active without an active OWNER', NEW.id
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_active_tenant_has_owner ON sys_tenant;
CREATE TRIGGER trg_active_tenant_has_owner
    BEFORE INSERT OR UPDATE OF status ON sys_tenant
    FOR EACH ROW EXECUTE FUNCTION guard_active_tenant_has_owner();

COMMENT ON COLUMN sys_tenant_invitation.platform_operator_ref IS
    'Opaque platform operator audit reference from verified Runtime Invocation Context';
