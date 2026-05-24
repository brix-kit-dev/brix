-- =============================================================================
-- V017: Identity 状态机 + Bootstrap Setup Flow 持久化
--
-- 依据:
--   docs/v2.0-平台超管功能最小实现-设计蓝图.md §16.8
--   docs-dev/超管功能改造计划-v2.0.md Phase 1 裁决补充
--
-- 核心裁决:
--   * Bootstrap anchor = sys_identity(status=PENDING_SETUP, password_hash=NULL)
--     + sys_platform_admin(role=BOOTSTRAP, status=ACTIVE)
--   * 正式超管 = role=PLATFORM_SUPER_ADMIN
--   * 平台管理员生命周期使用 ACTIVE/REVOKED，不再使用 disable/suspend 语义
-- =============================================================================

-- -----------------------------------------------------------------------------
-- sys_identity: 独立 IdentityStatus + 身份级 MFA 字段
-- -----------------------------------------------------------------------------

ALTER TABLE sys_identity
    ALTER COLUMN status SET DEFAULT 'PENDING_SETUP';

UPDATE sys_identity
SET status = CASE status
    WHEN 'PENDING' THEN 'PENDING_SETUP'
    WHEN 'ACTIVE' THEN 'ACTIVE'
    WHEN 'SUSPENDED' THEN 'DISABLED'
    WHEN 'INACTIVE' THEN 'DISABLED'
    WHEN 'DELETED' THEN 'DISABLED'
    ELSE status
END;

ALTER TABLE sys_identity
    ADD COLUMN IF NOT EXISTS mfa_secret_encrypted VARCHAR(512);

ALTER TABLE sys_identity
    ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE sys_identity
    ADD COLUMN IF NOT EXISTS mfa_bound_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_sys_identity_mfa_enabled
    ON sys_identity(mfa_enabled)
    WHERE mfa_enabled = FALSE;

COMMENT ON COLUMN sys_identity.status IS
    'IdentityStatus: PENDING_SETUP, ACTIVE, LOCKED, DISABLED';
COMMENT ON COLUMN sys_identity.mfa_secret_encrypted IS
    'Encrypted TOTP secret; never expose through API responses or logs';
COMMENT ON COLUMN sys_identity.mfa_enabled IS
    'Whether TOTP MFA has been bound for this identity';
COMMENT ON COLUMN sys_identity.mfa_bound_at IS
    'Timestamp when MFA was bound';

-- -----------------------------------------------------------------------------
-- sys_platform_admin: role/status model and revoke metadata
-- -----------------------------------------------------------------------------

UPDATE sys_platform_admin
SET status = CASE status
    WHEN 'ACTIVE' THEN 'ACTIVE'
    ELSE 'REVOKED'
END;

UPDATE sys_platform_admin
SET role = CASE role
    WHEN 'SUPER_ADMIN' THEN 'PLATFORM_SUPER_ADMIN'
    ELSE 'PLATFORM_SUPER_ADMIN'
END,
status = CASE
    WHEN role = 'SUPER_ADMIN' AND status = 'ACTIVE' THEN 'ACTIVE'
    ELSE 'REVOKED'
END;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'sys_platform_admin' AND column_name = 'disabled_at'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'sys_platform_admin' AND column_name = 'revoked_at'
    ) THEN
        ALTER TABLE sys_platform_admin RENAME COLUMN disabled_at TO revoked_at;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'sys_platform_admin' AND column_name = 'disabled_by'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'sys_platform_admin' AND column_name = 'revoked_by'
    ) THEN
        ALTER TABLE sys_platform_admin RENAME COLUMN disabled_by TO revoked_by;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'sys_platform_admin' AND column_name = 'disable_reason'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'sys_platform_admin' AND column_name = 'revoke_reason'
    ) THEN
        ALTER TABLE sys_platform_admin RENAME COLUMN disable_reason TO revoke_reason;
    END IF;
END $$;

ALTER TABLE sys_platform_admin
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ;

ALTER TABLE sys_platform_admin
    ADD COLUMN IF NOT EXISTS revoked_by BIGINT;

ALTER TABLE sys_platform_admin
    ADD COLUMN IF NOT EXISTS revoke_reason VARCHAR(512);

COMMENT ON COLUMN sys_platform_admin.role IS
    'PlatformAdminRole: PLATFORM_SUPER_ADMIN or BOOTSTRAP';
COMMENT ON COLUMN sys_platform_admin.status IS
    'PlatformAdminStatus: ACTIVE or REVOKED';
COMMENT ON COLUMN sys_platform_admin.revoked_at IS
    'Timestamp when the platform-admin grant was revoked';
COMMENT ON COLUMN sys_platform_admin.revoked_by IS
    'identity_id of the operator who revoked the grant';
COMMENT ON COLUMN sys_platform_admin.revoke_reason IS
    'Revoke reason (must not contain passwords, tokens, or secrets)';

-- -----------------------------------------------------------------------------
-- sys_bootstrap_state: global Stage A/B singleton
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sys_bootstrap_state (
    id                              BIGINT PRIMARY KEY,
    bootstrap_identity_id           BIGINT,
    setup_code_hash                 VARCHAR(128),
    setup_code_expires_at           TIMESTAMPTZ,
    setup_code_used_at              TIMESTAMPTZ,
    bootstrap_session_jti           VARCHAR(128),
    bootstrap_session_expires_at    TIMESTAMPTZ,
    bootstrap_session_used_at       TIMESTAMPTZ,
    completed_at                    TIMESTAMPTZ,
    completed_by_identity_id        BIGINT,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMPTZ,
    CONSTRAINT fk_bootstrap_state_identity
        FOREIGN KEY (bootstrap_identity_id) REFERENCES sys_identity(id),
    CONSTRAINT fk_bootstrap_state_completed_by
        FOREIGN KEY (completed_by_identity_id) REFERENCES sys_identity(id)
);

CREATE INDEX IF NOT EXISTS idx_sys_bootstrap_state_completed_at
    ON sys_bootstrap_state(completed_at);

COMMENT ON TABLE sys_bootstrap_state IS
    'Singleton bootstrap Stage A/B state. Row id=1 closes permanently when completed_at is set.';

-- -----------------------------------------------------------------------------
-- sys_setup_token: one-time setup tokens for formal admin onboarding/reset
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sys_setup_token (
    id              BIGINT PRIMARY KEY,
    identity_id     BIGINT NOT NULL,
    purpose         VARCHAR(64) NOT NULL,
    token_hash      VARCHAR(128) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    created_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ,
    CONSTRAINT fk_setup_token_identity
        FOREIGN KEY (identity_id) REFERENCES sys_identity(id) ON DELETE CASCADE,
    CONSTRAINT uk_sys_setup_token_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_sys_setup_token_identity
    ON sys_setup_token(identity_id);

CREATE INDEX IF NOT EXISTS idx_sys_setup_token_active
    ON sys_setup_token(identity_id, purpose, expires_at)
    WHERE used_at IS NULL;

COMMENT ON TABLE sys_setup_token IS
    'One-time setup tokens for completing password and MFA enrollment. Stores token hashes only.';

-- -----------------------------------------------------------------------------
-- Platform RBAC cleanup for v2.0 roles/permissions
-- -----------------------------------------------------------------------------

UPDATE auth_role
SET code = 'PLATFORM_SUPER_ADMIN',
    name = '平台超级管理员',
    description = 'Formal platform super administrator.',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'PLATFORM'
  AND code = 'SUPER_ADMIN';

UPDATE auth_role
SET status = 'INACTIVE', deleted = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'PLATFORM'
    AND code IN ('PLATFORM_ADMIN', 'SUPPORT_ADMIN', 'AUDITOR', 'PLATFORM_OPERATOR', 'PLATFORM_AUDITOR');

INSERT INTO auth_role (id, code, name, description, type, scope, status, sort_order, tenant_id,
                       created_at, updated_at, deleted)
VALUES
        ('role-platform-super-admin', 'PLATFORM_SUPER_ADMIN', '平台超级管理员',
         'Formal platform super administrator.',
         'SYSTEM', 'PLATFORM', 'ACTIVE', 10, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
        ('role-platform-bootstrap', 'BOOTSTRAP', '平台首启引导账号',
         'Passwordless bootstrap setup anchor. Not a formal platform administrator.',
         'SYSTEM', 'PLATFORM', 'ACTIVE', 5, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
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
        ('perm-platform-bypass',
         'platform:bypass',
         '内部权限旁路',
         'Backend-only bypass permission. Never emit to frontend JWT permissions.',
         'platform-admin', 'SYSTEM', 'ACTIVE', 1,
         'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
        ('perm-platform-tenant-manage',
         'platform:tenant:manage',
         '管理租户',
         'Backward-compatible coarse tenant management permission.',
         'platform-admin', 'BUTTON', 'ACTIVE', 9,
         'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
        ('perm-platform-tenant-view',
         'platform:tenant:view',
         '查看租户',
         'Backward-compatible coarse tenant read permission.',
         'platform-admin', 'BUTTON', 'ACTIVE', 10,
         'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
        ('perm-platform-tenant-read',
         'platform:tenant:read',
         '查看租户列表',
         'Read platform-scoped tenant list and details.',
         'platform-admin', 'BUTTON', 'ACTIVE', 11,
         'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-tenant-create',
     'platform:tenant:create',
     '创建租户',
     'Create a new tenant through the platform super-admin console.',
     'platform-admin', 'BUTTON', 'ACTIVE', 12,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-tenant-update-status',
     'platform:tenant:update-status',
     '变更租户状态',
     'Change tenant lifecycle status.',
     'platform-admin', 'BUTTON', 'ACTIVE', 13,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-manage',
     'platform:admin:manage',
     '管理平台超管',
     'Backward-compatible coarse platform-admin management permission.',
     'platform-admin', 'BUTTON', 'ACTIVE', 19,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-read',
     'platform:admin:read',
     '查看超管列表',
     'Read platform administrator list and details.',
     'platform-admin', 'BUTTON', 'ACTIVE', 20,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-create',
     'platform:admin:create',
     '新增超管',
     'Create a new platform administrator account.',
     'platform-admin', 'BUTTON', 'ACTIVE', 21,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-revoke',
     'platform:admin:revoke',
     '撤销超管授权',
     'Revoke an existing platform administrator grant.',
     'platform-admin', 'BUTTON', 'ACTIVE', 22,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-reset-password',
     'platform:admin:reset-password',
     '重置超管密码',
     'Reissue setup link for another platform administrator.',
     'platform-admin', 'BUTTON', 'ACTIVE', 23,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-change-own-password',
     'platform:admin:change-own-password',
     '修改自己密码',
     'Change the caller own password.',
     'platform-admin', 'BUTTON', 'ACTIVE', 24,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-audit-view',
     'platform:audit:view',
     '查看审计',
     'Backward-compatible coarse audit read permission.',
     'platform-admin', 'BUTTON', 'ACTIVE', 29,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-audit-read',
     'platform:audit:read',
     '查看审计日志',
     'Read platform audit logs with pagination and filtering.',
     'platform-admin', 'BUTTON', 'ACTIVE', 30,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-system-config',
     'platform:system:config',
     '系统配置',
     'Access system-level configuration.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 40,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-data-recovery',
     'platform:data:recovery',
     '数据恢复',
     'Perform data recovery and maintenance operations.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 41,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-bootstrap-read',
     'platform:bootstrap:read',
     '查看 Bootstrap 状态',
     'Read bootstrap status during Stage A.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 2,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-bootstrap-create-first-admin',
     'platform:bootstrap:create-first-admin',
     '创建首个正式超管',
     'Create the first formal platform super administrator during Bootstrap Stage A.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 3,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (code, tenant_id) DO UPDATE SET
        name = EXCLUDED.name,
        description = EXCLUDED.description,
        module = EXCLUDED.module,
        type = EXCLUDED.type,
        status = EXCLUDED.status,
        sort_order = EXCLUDED.sort_order,
        updated_at = CURRENT_TIMESTAMP,
        deleted = FALSE;

UPDATE auth_permission
SET status = 'INACTIVE', deleted = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'PLATFORM'
    AND code = 'platform:admin:disable';

DELETE FROM auth_role_permission rp
USING auth_role r
WHERE rp.role_id = r.id
    AND rp.tenant_id = 'PLATFORM'
    AND r.tenant_id = 'PLATFORM'
    AND r.code IN ('PLATFORM_ADMIN', 'SUPPORT_ADMIN', 'AUDITOR', 'PLATFORM_OPERATOR', 'PLATFORM_AUDITOR');

DELETE FROM auth_role_permission
WHERE tenant_id = 'PLATFORM'
    AND role_id IN ('role-platform-super-admin', 'role-platform-bootstrap');

DELETE FROM auth_role_permission rp
USING auth_permission p
WHERE rp.permission_id = p.id
    AND rp.tenant_id = 'PLATFORM'
    AND p.code = 'platform:admin:disable';

INSERT INTO auth_role_permission (id, role_id, permission_id, tenant_id,
                                  created_at, updated_at, deleted)
SELECT
    CONCAT('rp-platform-super-admin-', p.id),
    'role-platform-super-admin',
    p.id,
    'PLATFORM',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
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
    'platform:audit:read',
    'platform:system:config',
    'platform:data:recovery'
  )
ON CONFLICT (role_id, permission_id, tenant_id) DO UPDATE SET
        updated_at = CURRENT_TIMESTAMP,
        deleted = FALSE;

INSERT INTO auth_role_permission (id, role_id, permission_id, tenant_id,
                                  created_at, updated_at, deleted)
SELECT
    CONCAT('rp-platform-bootstrap-', p.id),
    'role-platform-bootstrap',
    p.id,
    'PLATFORM',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
FROM auth_permission p
WHERE p.tenant_id = 'PLATFORM'
  AND p.deleted = FALSE
  AND p.code IN ('platform:bootstrap:read', 'platform:bootstrap:create-first-admin')
ON CONFLICT (role_id, permission_id, tenant_id) DO UPDATE SET
        updated_at = CURRENT_TIMESTAMP,
        deleted = FALSE;