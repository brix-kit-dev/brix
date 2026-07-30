-- Pre-release clean initialization for the platform-auth Data Owner.
-- Reset boundary: development databases and Flyway history may be discarded.

CREATE TABLE IF NOT EXISTS auth_user (
    id                  VARCHAR(64)  NOT NULL PRIMARY KEY,
    username            VARCHAR(100) NOT NULL,
    email               VARCHAR(255),
    phone               VARCHAR(32),
    password_hash       VARCHAR(255),
    nickname            VARCHAR(100),
    avatar_url          VARCHAR(500),
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    tenant_id           VARCHAR(64)  NOT NULL,
    last_login_at       TIMESTAMP,
    login_count         INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP,
    CONSTRAINT uk_auth_user_email_tenant UNIQUE (email, tenant_id),
    CONSTRAINT uk_auth_user_phone_tenant UNIQUE (phone, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_auth_user_tenant ON auth_user(tenant_id);
CREATE INDEX IF NOT EXISTS idx_auth_user_status ON auth_user(status) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_user_email ON auth_user(email) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_user_phone ON auth_user(phone) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS auth_role (
    id                  VARCHAR(64)  NOT NULL PRIMARY KEY,
    code                VARCHAR(100) NOT NULL,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(500),
    type                VARCHAR(32)  NOT NULL DEFAULT 'CUSTOM',
    scope               VARCHAR(32)  NOT NULL DEFAULT 'TENANT',
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    sort_order          INTEGER      NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(64)  NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP,
    CONSTRAINT uk_auth_role_code_tenant UNIQUE (code, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_auth_role_tenant ON auth_role(tenant_id);
CREATE INDEX IF NOT EXISTS idx_auth_role_type ON auth_role(type) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS auth_permission (
    id                  VARCHAR(64)  NOT NULL PRIMARY KEY,
    code                VARCHAR(150) NOT NULL,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(500),
    module              VARCHAR(100),
    type                VARCHAR(32)  NOT NULL DEFAULT 'API',
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    sort_order          INTEGER      NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(64)  NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP,
    CONSTRAINT uk_auth_permission_code_tenant UNIQUE (code, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_auth_permission_tenant ON auth_permission(tenant_id);
CREATE INDEX IF NOT EXISTS idx_auth_permission_module ON auth_permission(module) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_permission_type ON auth_permission(type) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS auth_user_role (
    id                  VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id             VARCHAR(64) NOT NULL,
    role_id             VARCHAR(64) NOT NULL,
    tenant_id           VARCHAR(64) NOT NULL,
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP,
    CONSTRAINT uk_auth_user_role UNIQUE (user_id, role_id, tenant_id),
    CONSTRAINT fk_auth_user_role_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
    CONSTRAINT fk_auth_user_role_role FOREIGN KEY (role_id) REFERENCES auth_role(id)
);

CREATE INDEX IF NOT EXISTS idx_auth_user_role_user ON auth_user_role(user_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_user_role_role ON auth_user_role(role_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_user_role_tenant ON auth_user_role(tenant_id);

CREATE TABLE IF NOT EXISTS auth_role_permission (
    id                  VARCHAR(128) NOT NULL PRIMARY KEY,
    role_id             VARCHAR(64)  NOT NULL,
    permission_id       VARCHAR(64)  NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP,
    CONSTRAINT uk_auth_role_permission UNIQUE (role_id, permission_id, tenant_id),
    CONSTRAINT fk_auth_role_permission_role FOREIGN KEY (role_id) REFERENCES auth_role(id),
    CONSTRAINT fk_auth_role_permission_permission FOREIGN KEY (permission_id) REFERENCES auth_permission(id)
);

CREATE INDEX IF NOT EXISTS idx_auth_role_permission_role ON auth_role_permission(role_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_role_permission_perm ON auth_role_permission(permission_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_role_permission_tenant ON auth_role_permission(tenant_id);

CREATE TABLE IF NOT EXISTS auth_session (
    id                  VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id             VARCHAR(64) NOT NULL,
    token_hash          VARCHAR(255) NOT NULL,
    refresh_token_hash  VARCHAR(255),
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ip_address          VARCHAR(64),
    user_agent          VARCHAR(500),
    device_type         VARCHAR(32),
    tenant_id           VARCHAR(64) NOT NULL,
    expires_at          TIMESTAMP   NOT NULL,
    last_access_at      TIMESTAMP,
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted             BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP,
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES auth_user(id)
);

CREATE INDEX IF NOT EXISTS idx_auth_session_user ON auth_session(user_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_session_token ON auth_session(token_hash) WHERE deleted = FALSE AND status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_auth_session_tenant ON auth_session(tenant_id);
CREATE INDEX IF NOT EXISTS idx_auth_session_expires ON auth_session(expires_at) WHERE deleted = FALSE AND status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS auth_oauth_client (
    id                      VARCHAR(64)  NOT NULL PRIMARY KEY,
    client_id               VARCHAR(100) NOT NULL,
    client_secret_hash      VARCHAR(255),
    client_name             VARCHAR(100) NOT NULL,
    redirect_uris           JSONB,
    scopes                  JSONB,
    grant_types             JSONB,
    status                  VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    tenant_id               VARCHAR(64)  NOT NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP,
    created_by              VARCHAR(64),
    updated_by              VARCHAR(64),
    deleted                 BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    ext_varchar_1           VARCHAR(255),
    ext_varchar_2           VARCHAR(255),
    ext_varchar_3           VARCHAR(255),
    ext_varchar_4           VARCHAR(255),
    ext_varchar_5           VARCHAR(255),
    ext_int_1               INTEGER,
    ext_json_1              JSONB,
    ext_timestamp_1         TIMESTAMP,
    CONSTRAINT uk_auth_oauth_client_id UNIQUE (client_id)
);

CREATE INDEX IF NOT EXISTS idx_auth_oauth_client_tenant ON auth_oauth_client(tenant_id);
CREATE INDEX IF NOT EXISTS idx_auth_oauth_client_status ON auth_oauth_client(status) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS auth_audit_log (
    id                  VARCHAR(64) NOT NULL PRIMARY KEY,
    event_type          VARCHAR(64) NOT NULL,
    event_status        VARCHAR(32) NOT NULL,
    user_id             VARCHAR(64),
    username            VARCHAR(100),
    target_type         VARCHAR(64),
    target_id           VARCHAR(64),
    description         VARCHAR(500),
    detail              JSONB,
    ip_address          VARCHAR(64),
    user_agent          VARCHAR(500),
    device_type         VARCHAR(32),
    request_id          VARCHAR(64),
    request_path        VARCHAR(500),
    tenant_id           VARCHAR(64) NOT NULL,
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auth_audit_user ON auth_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_audit_event ON auth_audit_log(event_type);
CREATE INDEX IF NOT EXISTS idx_auth_audit_tenant ON auth_audit_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_auth_audit_time ON auth_audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_auth_audit_target ON auth_audit_log(target_type, target_id);

CREATE TABLE IF NOT EXISTS auth_refresh_token (
    id              BIGINT      NOT NULL PRIMARY KEY,
    token_id        VARCHAR(64) NOT NULL,
    identity_id     BIGINT      NOT NULL,
    admin_id        BIGINT,
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    revoke_reason   VARCHAR(64),
    CONSTRAINT uk_auth_refresh_token_id UNIQUE (token_id)
);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_identity
    ON auth_refresh_token(identity_id)
    WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_expires
    ON auth_refresh_token(expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE IF NOT EXISTS auth_context_selection_ticket (
    id                  BIGINT      NOT NULL PRIMARY KEY,
    ticket_hash         VARCHAR(64) NOT NULL,
    identity_id         BIGINT      NOT NULL,
    identity_token_jti  VARCHAR(64) NOT NULL,
    role_type           VARCHAR(16) NOT NULL,
    tenant_id           BIGINT      NOT NULL,
    ref_id              BIGINT      NOT NULL,
    context_id          VARCHAR(36) NOT NULL,
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMPTZ NOT NULL,
    consumed_at         TIMESTAMPTZ,
    CONSTRAINT uk_auth_context_ticket_hash UNIQUE (ticket_hash),
    CONSTRAINT ck_auth_context_ticket_role CHECK (role_type IN ('actor', 'subject'))
);

CREATE INDEX IF NOT EXISTS idx_auth_context_ticket_identity
    ON auth_context_selection_ticket(identity_id)
    WHERE consumed_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_auth_context_ticket_expires
    ON auth_context_selection_ticket(expires_at)
    WHERE consumed_at IS NULL;

INSERT INTO auth_role (id, code, name, description, type, scope, status, tenant_id, sort_order)
VALUES
    ('role-tenant-admin', 'TENANT_ADMIN', 'Tenant Admin', 'Tenant administrator', 'SYSTEM', 'TENANT', 'ACTIVE', 'default', 2),
    ('role-user', 'USER', 'User', 'Regular user', 'SYSTEM', 'TENANT', 'ACTIVE', 'default', 3),
    ('role-platform-super-admin', 'PLATFORM_SUPER_ADMIN', 'Platform Super Administrator',
     'Formal platform super administrator.', 'SYSTEM', 'PLATFORM', 'ACTIVE', 'PLATFORM', 10),
    ('role-platform-bootstrap', 'BOOTSTRAP', 'Platform Bootstrap',
     'Passwordless first-admin setup anchor. Not a formal platform administrator.', 'SYSTEM', 'PLATFORM', 'ACTIVE', 'PLATFORM', 5)
ON CONFLICT (code, tenant_id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    scope = EXCLUDED.scope,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

INSERT INTO auth_permission (id, code, name, description, module, type, status, sort_order,
                             tenant_id, created_at, updated_at, deleted)
VALUES
    ('perm-platform-bypass', 'platform:bypass', 'Internal permission bypass',
     'Backend-only permission. It must never be emitted into default platform-admin tokens.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 1, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-tenant-manage', 'platform:tenant:manage', 'Manage tenants',
     'Backward-compatible coarse tenant management permission.',
     'platform-admin', 'BUTTON', 'ACTIVE', 9, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-tenant-view', 'platform:tenant:view', 'View tenants',
     'Backward-compatible coarse tenant read permission.',
     'platform-admin', 'BUTTON', 'ACTIVE', 10, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-tenant-read', 'platform:tenant:read', 'Read tenants',
     'Read platform-scoped tenant list and details.',
     'platform-admin', 'BUTTON', 'ACTIVE', 11, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-tenant-create', 'platform:tenant:create', 'Create tenant',
     'Create a new tenant through the platform console.',
     'platform-admin', 'BUTTON', 'ACTIVE', 12, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-tenant-update-status', 'platform:tenant:update-status', 'Update tenant status',
     'Change tenant lifecycle status.',
     'platform-admin', 'BUTTON', 'ACTIVE', 13, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-manage', 'platform:admin:manage', 'Manage platform admins',
     'Backward-compatible coarse platform-admin management permission.',
     'platform-admin', 'BUTTON', 'ACTIVE', 19, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-read', 'platform:admin:read', 'Read platform admins',
     'Read platform administrator list and details.',
     'platform-admin', 'BUTTON', 'ACTIVE', 20, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-create', 'platform:admin:create', 'Create platform admin',
     'Create a new platform administrator account.',
     'platform-admin', 'BUTTON', 'ACTIVE', 21, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-revoke', 'platform:admin:revoke', 'Revoke platform admin',
     'Revoke an existing platform administrator grant.',
     'platform-admin', 'BUTTON', 'ACTIVE', 22, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-reset-password', 'platform:admin:reset-password', 'Reset platform admin password',
     'Reissue setup link for another platform administrator.',
     'platform-admin', 'BUTTON', 'ACTIVE', 23, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-change-own-password', 'platform:admin:change-own-password', 'Change own password',
     'Change the caller own password.',
     'platform-admin', 'BUTTON', 'ACTIVE', 24, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-audit-view', 'platform:audit:view', 'View audit',
     'Backward-compatible coarse audit read permission.',
     'platform-admin', 'BUTTON', 'ACTIVE', 29, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-audit-read', 'platform:audit:read', 'Read audit logs',
     'Read platform audit logs with pagination and filtering.',
     'platform-admin', 'BUTTON', 'ACTIVE', 30, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-system-config', 'platform:system:config', 'System config',
     'Access system-level configuration.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 40, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-license-read', 'platform:license:read', 'Read license',
     'Read installation license and tenant quota status.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 41, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-data-recovery', 'platform:data:recovery', 'Data recovery',
     'Perform data recovery and maintenance operations.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 42, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-bootstrap-read', 'platform:bootstrap:read', 'Read bootstrap status',
     'Read bootstrap status during Stage A.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 2, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-bootstrap-create-first-admin', 'platform:bootstrap:create-first-admin', 'Create first platform admin',
     'Create the first formal platform super administrator during Bootstrap Stage A.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 3, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (code, tenant_id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    module = EXCLUDED.module,
    type = EXCLUDED.type,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    deleted = FALSE,
    updated_at = CURRENT_TIMESTAMP;

DELETE FROM auth_role_permission
WHERE tenant_id = 'PLATFORM'
  AND role_id IN ('role-platform-super-admin', 'role-platform-bootstrap');

INSERT INTO auth_role_permission (id, role_id, permission_id, tenant_id,
                                  created_at, updated_at, deleted)
SELECT CONCAT('rp-platform-super-admin-', p.id), 'role-platform-super-admin', p.id,
       'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM auth_permission p
WHERE p.tenant_id = 'PLATFORM'
  AND p.deleted = FALSE
  AND p.code IN (
    'platform:bypass',
    'platform:tenant:manage',
    'platform:tenant:view',
    'platform:tenant:read',
    'platform:tenant:create',
    'platform:tenant:update-status',
    'platform:admin:manage',
    'platform:admin:read',
    'platform:admin:create',
    'platform:admin:revoke',
    'platform:admin:reset-password',
    'platform:admin:change-own-password',
    'platform:audit:view',
    'platform:audit:read',
    'platform:system:config',
    'platform:license:read',
    'platform:data:recovery'
  )
ON CONFLICT (role_id, permission_id, tenant_id) DO UPDATE SET
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

INSERT INTO auth_role_permission (id, role_id, permission_id, tenant_id,
                                  created_at, updated_at, deleted)
SELECT CONCAT('rp-platform-bootstrap-', p.id), 'role-platform-bootstrap', p.id,
       'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM auth_permission p
WHERE p.tenant_id = 'PLATFORM'
  AND p.deleted = FALSE
  AND p.code IN ('platform:bootstrap:read', 'platform:bootstrap:create-first-admin')
ON CONFLICT (role_id, permission_id, tenant_id) DO UPDATE SET
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

COMMENT ON TABLE auth_role IS 'Auth role catalog, including platform operational roles.';
COMMENT ON TABLE auth_permission IS 'Auth permission catalog.';
COMMENT ON TABLE auth_refresh_token IS 'Refresh token persistence for revocation and rotation.';
COMMENT ON TABLE auth_context_selection_ticket IS 'One-time context selection tickets bound to an identity-token jti.';
