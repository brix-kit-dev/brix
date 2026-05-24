-- =============================================================================
-- V016: Platform super-admin RBAC seed (v2.0)
--
-- References:
--   docs/v2.0-平台超管功能最小实现-设计蓝图.md §3.3 / §4 / §4.1
--   docs-dev/超管功能改造计划-v2.0.md Phase 1 task 1-7
--
-- Scope:
--   * Only two platform roles are seeded: PLATFORM_SUPER_ADMIN and BOOTSTRAP.
--   * tenant_id = 'PLATFORM' is a platform-scope sentinel, not a real tenant.
--   * platform:bypass stays active as a backend-only permission truth, but is
--     not emitted into JWT permissions by the issuer.
-- =============================================================================

INSERT INTO auth_role (id, code, name, description, type, scope, status, sort_order, tenant_id,
                       created_at, updated_at, deleted)
VALUES
    ('role-platform-super-admin', 'PLATFORM_SUPER_ADMIN', '平台超级管理员',
     'Formal platform super administrator.',
     'SYSTEM', 'PLATFORM', 'ACTIVE', 10, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('role-platform-bootstrap', 'BOOTSTRAP', '平台首启引导账号',
     'Passwordless first-admin setup anchor.',
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
    ('perm-platform-bypass', 'platform:bypass', '内部权限旁路',
     'Backend-only bypass permission. Never emit to frontend JWT permissions.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 1, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-tenant-manage', 'platform:tenant:manage', '管理租户',
     'Backward-compatible coarse tenant management permission.',
     'platform-admin', 'BUTTON', 'ACTIVE', 9, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-tenant-view', 'platform:tenant:view', '查看租户',
     'Backward-compatible coarse tenant read permission.',
     'platform-admin', 'BUTTON', 'ACTIVE', 10, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-tenant-read', 'platform:tenant:read', '查看租户列表',
     'Read platform-scoped tenant list and details.',
     'platform-admin', 'BUTTON', 'ACTIVE', 11, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-tenant-create', 'platform:tenant:create', '创建租户',
     'Create a new tenant through the platform super-admin console.',
     'platform-admin', 'BUTTON', 'ACTIVE', 12, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-tenant-update-status', 'platform:tenant:update-status', '变更租户状态',
     'Change tenant lifecycle status.',
     'platform-admin', 'BUTTON', 'ACTIVE', 13, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-manage', 'platform:admin:manage', '管理平台超管',
     'Backward-compatible coarse platform-admin management permission.',
     'platform-admin', 'BUTTON', 'ACTIVE', 19, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-read', 'platform:admin:read', '查看超管列表',
     'Read platform administrator list and details.',
     'platform-admin', 'BUTTON', 'ACTIVE', 20, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-create', 'platform:admin:create', '新增超管',
     'Create a new platform administrator account.',
     'platform-admin', 'BUTTON', 'ACTIVE', 21, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-revoke', 'platform:admin:revoke', '撤销超管授权',
     'Revoke an existing platform administrator grant.',
     'platform-admin', 'BUTTON', 'ACTIVE', 22, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-reset-password', 'platform:admin:reset-password', '重置超管密码',
     'Reissue setup link for another platform administrator.',
     'platform-admin', 'BUTTON', 'ACTIVE', 23, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-admin-change-own-password', 'platform:admin:change-own-password', '修改自己密码',
     'Change the caller own password.',
     'platform-admin', 'BUTTON', 'ACTIVE', 24, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-audit-view', 'platform:audit:view', '查看审计',
     'Backward-compatible coarse audit read permission.',
     'platform-admin', 'BUTTON', 'ACTIVE', 29, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-audit-read', 'platform:audit:read', '查看审计日志',
     'Read platform audit logs with pagination and filtering.',
     'platform-admin', 'BUTTON', 'ACTIVE', 30, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-system-config', 'platform:system:config', '系统配置',
     'Access system-level configuration.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 40, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-data-recovery', 'platform:data:recovery', '数据恢复',
     'Perform data recovery and maintenance operations.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 41, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-bootstrap-read', 'platform:bootstrap:read', '查看 Bootstrap 状态',
     'Read bootstrap status during Stage A.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 2, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('perm-platform-bootstrap-create-first-admin', 'platform:bootstrap:create-first-admin', '创建首位正式平台超管',
     'Create the first formal platform super administrator during Bootstrap Stage A.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 3, 'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (code, tenant_id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    module = EXCLUDED.module,
    type = EXCLUDED.type,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

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