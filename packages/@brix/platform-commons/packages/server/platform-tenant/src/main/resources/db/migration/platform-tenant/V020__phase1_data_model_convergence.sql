-- ============================================================================
-- V020: Phase 1 data model convergence for v3.1.3 multi-tenant baseline
-- ============================================================================
-- This migration brings databases that already executed earlier V007-V019
-- migrations to the frozen Phase 1 schema without relying on checksum rewrites.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- Context identifiers and authorization versions
-- ---------------------------------------------------------------------------
ALTER TABLE sys_tenant_member ADD COLUMN IF NOT EXISTS context_id UUID;
ALTER TABLE sys_tenant_member ADD COLUMN IF NOT EXISTS authz_version INTEGER NOT NULL DEFAULT 1;
UPDATE sys_tenant_member SET context_id = gen_random_uuid() WHERE context_id IS NULL;
ALTER TABLE sys_tenant_member ALTER COLUMN context_id SET DEFAULT gen_random_uuid();
ALTER TABLE sys_tenant_member ALTER COLUMN context_id SET NOT NULL;

ALTER TABLE sys_tenant_principal ADD COLUMN IF NOT EXISTS context_id UUID;
ALTER TABLE sys_tenant_principal ADD COLUMN IF NOT EXISTS authz_version INTEGER NOT NULL DEFAULT 1;
UPDATE sys_tenant_principal SET context_id = gen_random_uuid() WHERE context_id IS NULL;
ALTER TABLE sys_tenant_principal ALTER COLUMN context_id SET DEFAULT gen_random_uuid();
ALTER TABLE sys_tenant_principal ALTER COLUMN context_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_member_context_id') THEN
        ALTER TABLE sys_tenant_member ADD CONSTRAINT uk_member_context_id UNIQUE (context_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_member_tenant_id') THEN
        ALTER TABLE sys_tenant_member ADD CONSTRAINT uk_member_tenant_id UNIQUE (tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_principal_context_id') THEN
        ALTER TABLE sys_tenant_principal ADD CONSTRAINT uk_principal_context_id UNIQUE (context_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_principal_tenant_id') THEN
        ALTER TABLE sys_tenant_principal ADD CONSTRAINT uk_principal_tenant_id UNIQUE (tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_principal_status') THEN
        ALTER TABLE sys_tenant_principal ADD CONSTRAINT chk_principal_status CHECK (status IN ('ACTIVE', 'DISABLED', 'REVOKED'));
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION guard_context_id_immutable() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.context_id IS DISTINCT FROM OLD.context_id THEN
        RAISE EXCEPTION 'context_id is immutable for %.id=%', TG_TABLE_NAME, OLD.id
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_member_context_id_immutable ON sys_tenant_member;
CREATE TRIGGER trg_member_context_id_immutable
    BEFORE UPDATE ON sys_tenant_member
    FOR EACH ROW EXECUTE FUNCTION guard_context_id_immutable();

DROP TRIGGER IF EXISTS trg_principal_context_id_immutable ON sys_tenant_principal;
CREATE TRIGGER trg_principal_context_id_immutable
    BEFORE UPDATE ON sys_tenant_principal
    FOR EACH ROW EXECUTE FUNCTION guard_context_id_immutable();

-- ---------------------------------------------------------------------------
-- biz_user_profile strong references
-- ---------------------------------------------------------------------------
ALTER TABLE biz_user_profile ADD COLUMN IF NOT EXISTS member_id BIGINT;
ALTER TABLE biz_user_profile ADD COLUMN IF NOT EXISTS principal_id BIGINT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'biz_user_profile' AND column_name = 'ref_type'
    ) THEN
        EXECUTE 'UPDATE biz_user_profile SET member_id = ref_id WHERE ref_type = ''MEMBER'' AND member_id IS NULL';
        EXECUTE 'UPDATE biz_user_profile SET principal_id = ref_id WHERE ref_type = ''PRINCIPAL'' AND principal_id IS NULL';
    END IF;
END;
$$;

DO $$
DECLARE
    conflict_report JSONB;
BEGIN
    SELECT jsonb_agg(row_to_json(conflicts))
    INTO conflict_report
    FROM (
        SELECT 'invalid_reference_shape' AS reason, p.id, p.tenant_id, p.member_id, p.principal_id
        FROM biz_user_profile p
        WHERE (p.member_id IS NULL AND p.principal_id IS NULL)
           OR (p.member_id IS NOT NULL AND p.principal_id IS NOT NULL)
        UNION ALL
        SELECT 'missing_or_cross_tenant_member' AS reason, p.id, p.tenant_id, p.member_id, p.principal_id
        FROM biz_user_profile p
        LEFT JOIN sys_tenant_member m
            ON m.tenant_id = p.tenant_id AND m.id = p.member_id
        WHERE p.member_id IS NOT NULL AND m.id IS NULL
        UNION ALL
        SELECT 'missing_or_cross_tenant_principal' AS reason, p.id, p.tenant_id, p.member_id, p.principal_id
        FROM biz_user_profile p
        LEFT JOIN sys_tenant_principal pr
            ON pr.tenant_id = p.tenant_id AND pr.id = p.principal_id
        WHERE p.principal_id IS NOT NULL AND pr.id IS NULL
    ) conflicts;

    IF conflict_report IS NOT NULL THEN
        RAISE EXCEPTION 'V020 cannot strengthen biz_user_profile references because conflicts exist: %', conflict_report;
    END IF;
END;
$$;

ALTER TABLE biz_user_profile DROP CONSTRAINT IF EXISTS uk_profile_tenant_ref;
ALTER TABLE biz_user_profile DROP CONSTRAINT IF EXISTS chk_profile_ref_type;
ALTER TABLE biz_user_profile DROP CONSTRAINT IF EXISTS fk_profile_member;
ALTER TABLE biz_user_profile DROP CONSTRAINT IF EXISTS fk_profile_principal;
ALTER TABLE biz_user_profile DROP CONSTRAINT IF EXISTS uk_profile_member;
ALTER TABLE biz_user_profile DROP CONSTRAINT IF EXISTS uk_profile_principal;
ALTER TABLE biz_user_profile DROP CONSTRAINT IF EXISTS chk_profile_ref_exclusive;

ALTER TABLE biz_user_profile ADD CONSTRAINT fk_profile_member
    FOREIGN KEY (tenant_id, member_id) REFERENCES sys_tenant_member(tenant_id, id);
ALTER TABLE biz_user_profile ADD CONSTRAINT fk_profile_principal
    FOREIGN KEY (tenant_id, principal_id) REFERENCES sys_tenant_principal(tenant_id, id);
ALTER TABLE biz_user_profile ADD CONSTRAINT uk_profile_member UNIQUE (tenant_id, member_id);
ALTER TABLE biz_user_profile ADD CONSTRAINT uk_profile_principal UNIQUE (tenant_id, principal_id);
ALTER TABLE biz_user_profile ADD CONSTRAINT chk_profile_ref_exclusive CHECK (
    (member_id IS NOT NULL AND principal_id IS NULL)
 OR (member_id IS NULL     AND principal_id IS NOT NULL)
);

DROP INDEX IF EXISTS idx_user_profile_ref;
CREATE INDEX IF NOT EXISTS idx_user_profile_member ON biz_user_profile(tenant_id, member_id);
CREATE INDEX IF NOT EXISTS idx_user_profile_principal ON biz_user_profile(tenant_id, principal_id);

ALTER TABLE biz_user_profile DROP COLUMN IF EXISTS ref_type;
ALTER TABLE biz_user_profile DROP COLUMN IF EXISTS ref_id;

-- ---------------------------------------------------------------------------
-- Invitation table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_tenant_invitation (
    id                   BIGINT       PRIMARY KEY,
    tenant_id            BIGINT       NOT NULL,
    target_type          VARCHAR(20)  NOT NULL,
    target_role          VARCHAR(20)  NOT NULL,
    invitee_email        VARCHAR(255),
    invitee_phone        VARCHAR(50),
    token_hash           VARCHAR(128) NOT NULL,
    invited_by_member_id BIGINT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    expires_at           TIMESTAMPTZ  NOT NULL,
    accepted_at          TIMESTAMPTZ,
    revoked_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ,
    CONSTRAINT fk_invite_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE CASCADE,
    CONSTRAINT fk_invite_member FOREIGN KEY (tenant_id, invited_by_member_id) REFERENCES sys_tenant_member(tenant_id, id),
    CONSTRAINT chk_invite_target_type CHECK (target_type IN ('MEMBER', 'PRINCIPAL')),
    CONSTRAINT chk_invite_status CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT chk_invite_target_role CHECK (
        (target_type = 'MEMBER' AND target_role IN ('OWNER', 'ADMIN', 'MEMBER'))
     OR (target_type = 'PRINCIPAL' AND target_role IN ('CUSTOMER', 'GUEST'))
    )
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_invite_token ON sys_tenant_invitation(token_hash);
CREATE INDEX IF NOT EXISTS idx_invite_tenant ON sys_tenant_invitation(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invite_status ON sys_tenant_invitation(tenant_id, status);

-- ---------------------------------------------------------------------------
-- Last active OWNER guard
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION guard_last_owner() RETURNS TRIGGER AS $$
DECLARE
    owner_count INTEGER;
    locked_tenant_id BIGINT;
BEGIN
    locked_tenant_id := OLD.tenant_id;

    IF (TG_OP = 'DELETE' AND OLD.member_type = 'OWNER' AND OLD.status = 'ACTIVE')
       OR (TG_OP = 'UPDATE' AND OLD.member_type = 'OWNER' AND OLD.status = 'ACTIVE'
           AND (NEW.member_type IS DISTINCT FROM 'OWNER'
                OR NEW.status IS DISTINCT FROM 'ACTIVE'
                OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id)) THEN
        PERFORM pg_advisory_xact_lock(hashtext('tenant-owner:' || locked_tenant_id::text));

        SELECT COUNT(*) INTO owner_count
        FROM sys_tenant_member
        WHERE tenant_id = locked_tenant_id
          AND member_type = 'OWNER'
          AND status = 'ACTIVE'
          AND id <> OLD.id;

        IF owner_count = 0 THEN
            RAISE EXCEPTION 'TENANT_LAST_OWNER_PROTECTED: tenant % must keep at least one active OWNER', locked_tenant_id
                USING ERRCODE = '23514';
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_guard_last_owner ON sys_tenant_member;
CREATE TRIGGER trg_guard_last_owner
    BEFORE UPDATE OR DELETE ON sys_tenant_member
    FOR EACH ROW EXECUTE FUNCTION guard_last_owner();

-- ---------------------------------------------------------------------------
-- Installation-level tenant quota
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_installation_quota (
    installation_id VARCHAR(100) PRIMARY KEY,
    quota           INTEGER      NOT NULL,
    used            INTEGER      NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_installation_quota_non_negative CHECK (quota >= 0 AND used >= 0),
    CONSTRAINT chk_installation_quota_used_lte_quota CHECK (used <= quota)
);

INSERT INTO sys_installation_quota (installation_id, quota, used)
SELECT 'default', GREATEST(3, active_count), active_count
FROM (
    SELECT COUNT(*)::INTEGER AS active_count
    FROM sys_tenant
    WHERE status IN ('ACTIVE', 'TRIAL')
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_installation_quota WHERE installation_id = 'default'
);

-- ---------------------------------------------------------------------------
-- Audit table split
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_tenant_audit_log (
    id            BIGINT       PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL,
    actor_ref_id  BIGINT,
    action        VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(64)  NOT NULL,
    resource_id   VARCHAR(128),
    description   TEXT,
    context       JSONB,
    success       BOOLEAN      NOT NULL DEFAULT TRUE,
    error_code    VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tenant_audit_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)
);
CREATE INDEX IF NOT EXISTS idx_tenant_audit_tenant_time ON biz_tenant_audit_log(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tenant_audit_resource ON biz_tenant_audit_log(tenant_id, resource_type, resource_id);

CREATE TABLE IF NOT EXISTS sys_platform_audit_log (
    id                   BIGINT       PRIMARY KEY,
    operator_identity_id BIGINT,
    action               VARCHAR(64)  NOT NULL,
    resource_type        VARCHAR(64)  NOT NULL,
    resource_id          VARCHAR(128),
    affected_tenants     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    description          TEXT,
    context              JSONB,
    success              BOOLEAN      NOT NULL DEFAULT TRUE,
    error_code           VARCHAR(64),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_platform_audit_time ON sys_platform_audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_platform_audit_operator ON sys_platform_audit_log(operator_identity_id);

COMMENT ON TABLE sys_installation_quota IS 'Deployment instance tenant quota, locked with SELECT FOR UPDATE by application services';
COMMENT ON TABLE sys_tenant_invitation IS 'Tenant invitation table storing only token hashes';
COMMENT ON TABLE biz_tenant_audit_log IS 'Tenant-scoped audit log with mandatory tenant_id';
COMMENT ON TABLE sys_platform_audit_log IS 'Platform audit log for cross-tenant and platform operations';