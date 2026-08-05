-- Pre-release clean initialization for the platform-tenant Data Owner.
-- Reset boundary: development databases and Flyway history may be discarded.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS sys_tenant (
    id                       BIGINT       PRIMARY KEY,
    code                     VARCHAR(64)  NOT NULL,
    name                     VARCHAR(256) NOT NULL,
    status                   VARCHAR(32)  NOT NULL DEFAULT 'PENDING_ACTIVATION',
    default_locale           VARCHAR(20),
    default_timezone         VARCHAR(50),
    default_date_format      VARCHAR(20),
    default_time_format      VARCHAR(5),
    default_currency         VARCHAR(10),
    default_theme            VARCHAR(10),
    session_timeout_min      INTEGER,
    mfa_policy               VARCHAR(20),
    allowed_login_methods    JSONB        NOT NULL DEFAULT '["phone_sms", "email_password"]'::jsonb,
    password_policy          JSONB        NOT NULL DEFAULT '{"minLength":8,"requireUppercase":false,"requireLowercase":true,"requireNumbers":true,"requireSpecialChars":false,"maxAgeDays":0,"historyCount":0}'::jsonb,
    notification_channels    JSONB        NOT NULL DEFAULT '["in_app"]'::jsonb,
    business_hours           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    settings                 JSONB        NOT NULL DEFAULT '{}'::jsonb,
    logo_url                 VARCHAR(512),
    favicon_url              VARCHAR(512),
    primary_color            VARCHAR(20),
    secondary_color          VARCHAR(20),
    login_page_title         VARCHAR(256),
    login_page_subtitle      VARCHAR(512),
    login_page_bg_url        VARCHAR(512),
    max_users                INTEGER      NOT NULL DEFAULT 0,
    max_principals           INTEGER      NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ,
    CONSTRAINT uk_sys_tenant_code UNIQUE (code),
    CONSTRAINT chk_sys_tenant_default_theme CHECK (default_theme IS NULL OR default_theme IN ('LIGHT', 'DARK', 'SYSTEM', 'light', 'dark', 'system')),
    CONSTRAINT chk_sys_tenant_mfa_policy CHECK (mfa_policy IS NULL OR mfa_policy IN ('DISABLED', 'OPTIONAL', 'REQUIRED')),
    CONSTRAINT chk_sys_tenant_quota_non_negative CHECK (max_users >= 0 AND max_principals >= 0)
);

CREATE INDEX IF NOT EXISTS idx_sys_tenant_status ON sys_tenant(status);
CREATE INDEX IF NOT EXISTS idx_sys_tenant_created_at ON sys_tenant(created_at DESC);

CREATE TABLE IF NOT EXISTS sys_tenant_member (
    id                       BIGINT       PRIMARY KEY,
    tenant_id                BIGINT       NOT NULL,
    identity_id              BIGINT       NOT NULL,
    context_id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    authz_version            INTEGER      NOT NULL DEFAULT 1,
    member_type              VARCHAR(32)  NOT NULL DEFAULT 'MEMBER',
    status                   VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    joined_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ,
    CONSTRAINT fk_tenant_member_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE CASCADE,
    CONSTRAINT uk_sys_tenant_member_tenant_identity UNIQUE (tenant_id, identity_id),
    CONSTRAINT uk_member_context_id UNIQUE (context_id),
    CONSTRAINT uk_member_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_member_type CHECK (member_type IN ('OWNER', 'ADMIN', 'MEMBER')),
    CONSTRAINT chk_member_status CHECK (status IN ('ACTIVE', 'INVITED', 'DISABLED', 'REVOKED')),
    CONSTRAINT chk_member_authz_version_positive CHECK (authz_version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_sys_tenant_member_tenant ON sys_tenant_member(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sys_tenant_member_identity ON sys_tenant_member(identity_id);
CREATE INDEX IF NOT EXISTS idx_sys_tenant_member_type ON sys_tenant_member(tenant_id, member_type);
CREATE INDEX IF NOT EXISTS idx_sys_tenant_member_status ON sys_tenant_member(tenant_id, status);

CREATE TABLE IF NOT EXISTS sys_tenant_principal (
    id                       BIGINT       PRIMARY KEY,
    tenant_id                BIGINT       NOT NULL,
    identity_id              BIGINT       NOT NULL,
    context_id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    authz_version            INTEGER      NOT NULL DEFAULT 1,
    principal_type           VARCHAR(20)  NOT NULL,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_name             VARCHAR(100),
    joined_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_access_at           TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ,
    CONSTRAINT fk_principal_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE CASCADE,
    CONSTRAINT uk_principal_tenant_identity UNIQUE (tenant_id, identity_id),
    CONSTRAINT uk_principal_context_id UNIQUE (context_id),
    CONSTRAINT uk_principal_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_principal_type CHECK (principal_type IN ('CUSTOMER', 'GUEST')),
    CONSTRAINT chk_principal_status CHECK (status IN ('ACTIVE', 'DISABLED', 'REVOKED')),
    CONSTRAINT chk_principal_authz_version_positive CHECK (authz_version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_principal_tenant ON sys_tenant_principal(tenant_id);
CREATE INDEX IF NOT EXISTS idx_principal_identity ON sys_tenant_principal(identity_id);
CREATE INDEX IF NOT EXISTS idx_principal_status ON sys_tenant_principal(tenant_id, status);

CREATE TABLE IF NOT EXISTS sys_organization (
    id                       BIGINT       PRIMARY KEY,
    tenant_id                BIGINT       NOT NULL,
    parent_id                BIGINT,
    code                     VARCHAR(64)  NOT NULL,
    name                     VARCHAR(256) NOT NULL,
    description              TEXT,
    org_type                 VARCHAR(32),
    sort_order               INTEGER      NOT NULL DEFAULT 0,
    status                   VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ,
    CONSTRAINT fk_organization_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE CASCADE,
    CONSTRAINT fk_organization_parent FOREIGN KEY (parent_id) REFERENCES sys_organization(id),
    CONSTRAINT uk_sys_organization_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_sys_organization_tenant ON sys_organization(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sys_organization_parent ON sys_organization(parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_organization_tenant_parent ON sys_organization(tenant_id, parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_organization_tenant_status ON sys_organization(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_sys_organization_sort ON sys_organization(tenant_id, parent_id, sort_order);

CREATE TABLE IF NOT EXISTS biz_user_profile (
    id                       BIGINT       PRIMARY KEY,
    tenant_id                BIGINT       NOT NULL,
    member_id                BIGINT,
    principal_id             BIGINT,
    nickname                 VARCHAR(100),
    avatar_url               VARCHAR(512),
    display_name             VARCHAR(100),
    preferences              JSONB        NOT NULL DEFAULT '{}'::jsonb,
    extended                 JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ,
    CONSTRAINT fk_profile_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE CASCADE,
    CONSTRAINT fk_profile_member FOREIGN KEY (tenant_id, member_id) REFERENCES sys_tenant_member(tenant_id, id),
    CONSTRAINT fk_profile_principal FOREIGN KEY (tenant_id, principal_id) REFERENCES sys_tenant_principal(tenant_id, id),
    CONSTRAINT uk_profile_member UNIQUE (tenant_id, member_id),
    CONSTRAINT uk_profile_principal UNIQUE (tenant_id, principal_id),
    CONSTRAINT chk_profile_exactly_one_ref CHECK (
        (member_id IS NOT NULL AND principal_id IS NULL)
        OR (member_id IS NULL AND principal_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_user_profile_tenant ON biz_user_profile(tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_profile_member ON biz_user_profile(tenant_id, member_id);
CREATE INDEX IF NOT EXISTS idx_user_profile_principal ON biz_user_profile(tenant_id, principal_id);

CREATE TABLE IF NOT EXISTS sys_tenant_config (
    id                       BIGINT       PRIMARY KEY,
    tenant_id                BIGINT       NOT NULL,
    config_namespace         VARCHAR(100) NOT NULL,
    config_key               VARCHAR(200) NOT NULL,
    config_value             JSONB        NOT NULL,
    config_type              VARCHAR(20)  NOT NULL DEFAULT 'STRING',
    description              VARCHAR(500),
    is_sensitive             BOOLEAN      NOT NULL DEFAULT FALSE,
    is_readonly              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by               BIGINT,
    CONSTRAINT fk_tenant_config_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE CASCADE,
    CONSTRAINT uk_tenant_config UNIQUE (tenant_id, config_namespace, config_key),
    CONSTRAINT chk_config_type CHECK (config_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON', 'ENUM'))
);

CREATE INDEX IF NOT EXISTS idx_tenant_config_tenant ON sys_tenant_config(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenant_config_ns ON sys_tenant_config(tenant_id, config_namespace);

CREATE TABLE IF NOT EXISTS sys_tenant_invitation (
    id                       BIGINT       PRIMARY KEY,
    tenant_id                BIGINT       NOT NULL,
    target_type              VARCHAR(20)  NOT NULL DEFAULT 'MEMBER',
    target_role              VARCHAR(20)  NOT NULL DEFAULT 'OWNER',
    invitation_purpose       VARCHAR(32)  NOT NULL DEFAULT 'FIRST_OWNER',
    inviter_type             VARCHAR(32)  NOT NULL DEFAULT 'PLATFORM_ADMIN',
    platform_admin_id        BIGINT,
    platform_operator_ref    VARCHAR(128),
    invitee_email            VARCHAR(255),
    invitee_phone            VARCHAR(50),
    token_hash               VARCHAR(128) NOT NULL,
    invited_by_member_id     BIGINT,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    expires_at               TIMESTAMPTZ  NOT NULL,
    accepted_at              TIMESTAMPTZ,
    revoked_at               TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ,
    CONSTRAINT fk_invite_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE CASCADE,
    CONSTRAINT fk_invite_member FOREIGN KEY (tenant_id, invited_by_member_id) REFERENCES sys_tenant_member(tenant_id, id),
    CONSTRAINT uk_invite_token UNIQUE (token_hash),
    CONSTRAINT chk_invite_target_type CHECK (target_type IN ('MEMBER', 'PRINCIPAL')),
    CONSTRAINT chk_invite_target_role CHECK (target_role IN ('OWNER', 'ADMIN', 'MEMBER')),
    CONSTRAINT chk_invite_purpose CHECK (invitation_purpose IN ('FIRST_OWNER', 'TENANT_MEMBER')),
    CONSTRAINT chk_invite_inviter_type CHECK (inviter_type IN ('PLATFORM_ADMIN', 'TENANT_MEMBER')),
    CONSTRAINT chk_invite_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT chk_invite_recipient CHECK (invitee_email IS NOT NULL OR invitee_phone IS NOT NULL),
    CONSTRAINT chk_invite_inviter_scope CHECK (
        (inviter_type = 'PLATFORM_ADMIN'
            AND platform_operator_ref IS NOT NULL
            AND btrim(platform_operator_ref) <> ''
            AND invited_by_member_id IS NULL)
     OR (inviter_type = 'TENANT_MEMBER'
            AND invited_by_member_id IS NOT NULL
            AND platform_operator_ref IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_invite_tenant ON sys_tenant_invitation(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invite_status ON sys_tenant_invitation(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_invite_purpose_status ON sys_tenant_invitation(tenant_id, invitation_purpose, status);
CREATE INDEX IF NOT EXISTS idx_invite_email ON sys_tenant_invitation(invitee_email);

CREATE TABLE IF NOT EXISTS sys_installation_quota (
    installation_id          VARCHAR(100) PRIMARY KEY,
    quota                    INTEGER      NOT NULL,
    used                     INTEGER      NOT NULL DEFAULT 0,
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_installation_quota_non_negative CHECK (quota >= 0 AND used >= 0),
    CONSTRAINT chk_installation_quota_used_lte_quota CHECK (used <= quota)
);

INSERT INTO sys_installation_quota (installation_id, quota, used, updated_at)
VALUES ('default', 3, 0, CURRENT_TIMESTAMP)
ON CONFLICT (installation_id) DO UPDATE SET
    quota = EXCLUDED.quota,
    updated_at = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS biz_audit_log (
    id                       BIGINT       PRIMARY KEY,
    tenant_id                BIGINT,
    owner_member_id          BIGINT,
    owner_org_id             BIGINT,
    created_by               BIGINT       NOT NULL,
    action                   VARCHAR(64)  NOT NULL,
    resource_type            VARCHAR(64)  NOT NULL,
    resource_id              VARCHAR(128),
    description              TEXT,
    client_ip                VARCHAR(45),
    user_agent               TEXT,
    request_id               VARCHAR(128),
    context                  JSONB,
    success                  BOOLEAN      NOT NULL DEFAULT TRUE,
    error_message            TEXT,
    error_code               VARCHAR(64),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_biz_audit_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id),
    CONSTRAINT fk_biz_audit_member FOREIGN KEY (tenant_id, owner_member_id) REFERENCES sys_tenant_member(tenant_id, id),
    CONSTRAINT fk_biz_audit_org FOREIGN KEY (owner_org_id) REFERENCES sys_organization(id)
);

CREATE INDEX IF NOT EXISTS idx_biz_audit_log_tenant ON biz_audit_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_resource ON biz_audit_log(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_created_by ON biz_audit_log(created_by);
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_created_at ON biz_audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_tenant_time ON biz_audit_log(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_action ON biz_audit_log(tenant_id, action);
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_request_id ON biz_audit_log(request_id);
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_owner_member ON biz_audit_log(owner_member_id);
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_owner_org ON biz_audit_log(owner_org_id);
CREATE INDEX IF NOT EXISTS idx_biz_audit_log_failures ON biz_audit_log(tenant_id, created_at DESC) WHERE success = FALSE;

CREATE TABLE IF NOT EXISTS biz_tenant_audit_log (
    id                       BIGINT       PRIMARY KEY,
    tenant_id                BIGINT       NOT NULL,
    actor_ref_id             BIGINT,
    action                   VARCHAR(64)  NOT NULL,
    resource_type            VARCHAR(64)  NOT NULL,
    resource_id              VARCHAR(128),
    description              TEXT,
    context                  JSONB,
    success                  BOOLEAN      NOT NULL DEFAULT TRUE,
    error_code               VARCHAR(64),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_audit_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tenant_audit_tenant_time ON biz_tenant_audit_log(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tenant_audit_resource ON biz_tenant_audit_log(tenant_id, resource_type, resource_id);

CREATE TABLE IF NOT EXISTS platform_tenant_outbox (
    message_id               VARCHAR(64)  PRIMARY KEY,
    event_id                 VARCHAR(64)  NOT NULL,
    message_kind             VARCHAR(20)  NOT NULL DEFAULT 'EVENT',
    message_type             VARCHAR(128) NOT NULL,
    event_type               VARCHAR(128) NOT NULL,
    schema_version           VARCHAR(16)  NOT NULL DEFAULT '1.0.0',
    tenant_id                BIGINT,
    partition_key            VARCHAR(128) NOT NULL,
    payload                  JSONB        NOT NULL,
    reliability              VARCHAR(20)  NOT NULL DEFAULT 'CRITICAL',
    producer_plugin_id       VARCHAR(128) NOT NULL DEFAULT 'platform-tenant',
    scope                    VARCHAR(20)  NOT NULL DEFAULT 'TENANT',
    correlation_id           VARCHAR(128) NOT NULL,
    causation_id             VARCHAR(128),
    traceparent              VARCHAR(128),
    tracestate               VARCHAR(512),
    status                   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    occurred_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempt_count            INTEGER      NOT NULL DEFAULT 0,
    claim_owner              VARCHAR(128),
    claim_until              TIMESTAMPTZ,
    published_at             TIMESTAMPTZ,
    last_error_code          VARCHAR(128),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_platform_tenant_outbox_message_id UNIQUE (message_id),
    CONSTRAINT chk_platform_tenant_outbox_status CHECK (status IN ('PENDING', 'IN_FLIGHT', 'PUBLISHED', 'PARKED')),
    CONSTRAINT chk_platform_tenant_outbox_message_kind CHECK (message_kind IN ('EVENT', 'COMMAND')),
    CONSTRAINT chk_platform_tenant_outbox_scope CHECK (
        (scope = 'TENANT' AND tenant_id IS NOT NULL)
        OR (scope IN ('PLATFORM', 'GLOBAL') AND tenant_id IS NULL)
    ),
    CONSTRAINT chk_platform_tenant_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_platform_tenant_outbox_due ON platform_tenant_outbox(status, available_at);
CREATE INDEX IF NOT EXISTS idx_platform_tenant_outbox_claim ON platform_tenant_outbox(claim_owner, claim_until);

CREATE TABLE IF NOT EXISTS platform_tenant_inbox (
    handler_id               VARCHAR(128) NOT NULL,
    message_id               VARCHAR(64)  NOT NULL,
    message_kind             VARCHAR(20)  NOT NULL DEFAULT 'EVENT',
    message_type             VARCHAR(128) NOT NULL,
    schema_version           VARCHAR(16)  NOT NULL DEFAULT '1.0.0',
    tenant_id                BIGINT,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'PROCESSED',
    processed_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_platform_tenant_inbox PRIMARY KEY (handler_id, message_id),
    CONSTRAINT chk_platform_tenant_inbox_message_kind CHECK (message_kind IN ('EVENT', 'COMMAND')),
    CONSTRAINT chk_platform_tenant_inbox_status CHECK (status IN ('PROCESSING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_platform_tenant_inbox_tenant ON platform_tenant_inbox(tenant_id);

CREATE TABLE IF NOT EXISTS platform_tenant_first_owner_projection (
    tenant_id                BIGINT       PRIMARY KEY,
    message_id               VARCHAR(64)  NOT NULL,
    owner_member_id          BIGINT       NOT NULL,
    profile_id               BIGINT       NOT NULL,
    invitation_id            BIGINT       NOT NULL,
    projected_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_first_owner_projection_message UNIQUE (message_id),
    CONSTRAINT fk_first_owner_projection_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE CASCADE,
    CONSTRAINT fk_first_owner_projection_owner FOREIGN KEY (tenant_id, owner_member_id) REFERENCES sys_tenant_member(tenant_id, id),
    CONSTRAINT fk_first_owner_projection_profile FOREIGN KEY (profile_id) REFERENCES biz_user_profile(id),
    CONSTRAINT fk_first_owner_projection_invitation FOREIGN KEY (invitation_id) REFERENCES sys_tenant_invitation(id)
);

CREATE INDEX IF NOT EXISTS idx_first_owner_projection_message ON platform_tenant_first_owner_projection(message_id);

CREATE TABLE IF NOT EXISTS platform_tenant_command_idempotency (
    command_id               VARCHAR(64)  PRIMARY KEY,
    handler_id               VARCHAR(128) NOT NULL,
    idempotency_scope        VARCHAR(128) NOT NULL,
    idempotency_key          VARCHAR(128) NOT NULL,
    tenant_id                BIGINT,
    result_payload           JSONB,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_platform_tenant_command_idempotency UNIQUE (handler_id, idempotency_scope, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_platform_tenant_command_idempotency_tenant ON platform_tenant_command_idempotency(tenant_id);

CREATE OR REPLACE FUNCTION guard_immutable_context_id() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.context_id IS DISTINCT FROM OLD.context_id THEN
        RAISE EXCEPTION 'context_id is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_member_context_id_immutable
    BEFORE UPDATE ON sys_tenant_member
    FOR EACH ROW EXECUTE FUNCTION guard_immutable_context_id();

CREATE TRIGGER trg_principal_context_id_immutable
    BEFORE UPDATE ON sys_tenant_principal
    FOR EACH ROW EXECUTE FUNCTION guard_immutable_context_id();

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

CREATE TRIGGER trg_guard_last_owner
    BEFORE UPDATE OR DELETE ON sys_tenant_member
    FOR EACH ROW EXECUTE FUNCTION guard_last_owner();

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

CREATE TRIGGER trg_active_tenant_has_owner
    BEFORE INSERT OR UPDATE OF status ON sys_tenant
    FOR EACH ROW EXECUTE FUNCTION guard_active_tenant_has_owner();

COMMENT ON TABLE sys_tenant IS 'Platform tenant directory root owned by platform-tenant.';
COMMENT ON TABLE sys_tenant_member IS 'Actor membership table. identity_id is an opaque identity Owner reference, not a cross-Owner FK.';
COMMENT ON TABLE sys_tenant_principal IS 'Subject access table. identity_id is an opaque identity Owner reference, not a cross-Owner FK.';
COMMENT ON TABLE sys_tenant_invitation IS 'Tenant invitation table storing only token hashes and opaque platform operator references.';
COMMENT ON COLUMN sys_tenant_invitation.platform_operator_ref IS 'Opaque platform operator audit reference from verified Runtime Invocation Context.';
COMMENT ON TABLE sys_installation_quota IS 'Deployment instance tenant quota, locked with SELECT FOR UPDATE by application services.';
COMMENT ON TABLE platform_tenant_outbox IS 'Canonical reliable outbox owned by platform-tenant.';
COMMENT ON TABLE platform_tenant_inbox IS 'Canonical reliable inbox owned by platform-tenant.';
