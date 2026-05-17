-- =============================================================================
-- V016: 平台超管 RBAC 角色与权限 Seed
--
-- 蓝图依据:
--   v1.0-平台超管最小实现-唯一真相来源.md §3.3 (RBAC Seed)
--   SSOT 中标为 V013，但本仓库 V013 已用于 password_must_change，故命名 V016。
--
-- 设计说明:
--   * 角色与权限均使用 tenant_id = 'PLATFORM' 作为系统级哨兵值，与任何真实租户隔离。
--   * scope = 'PLATFORM' 区分平台角色与租户角色。
--   * 权限码完整来自 PlatformPermissions.java（编译期常量），确保运行时一致。
--   * 角色→权限绑定表 (auth_role_permission) 遵循最小权限原则。
--
-- 安全红线:
--   * 禁止将这些角色 seed 到任何 tenant_id ≠ 'PLATFORM' 的行中。
--   * 禁止在此 seed 中以字符串字面量写入权限码（已注释说明每个码对应哪个 Java 常量）。
--
-- 幂等性:
--   * 全部使用 INSERT ... ON CONFLICT DO NOTHING，重复执行安全。
-- =============================================================================

-- =========================================================
-- 1. Platform Admin Roles (auth_role)
-- =========================================================

INSERT INTO auth_role (id, code, name, description, type, scope, status, sort_order, tenant_id,
                       created_at, updated_at, deleted)
VALUES
    -- SUPER_ADMIN: full system access (PlatformPermissions: bypass + all)
    ('role-platform-super-admin',
     'SUPER_ADMIN',
     '平台超级管理员',
     'Full system access. Reserved for infrastructure management and emergency operations.',
     'SYSTEM', 'PLATFORM', 'ACTIVE', 10,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

    -- PLATFORM_ADMIN: day-to-day platform operations (no system config / data recovery)
    ('role-platform-admin',
     'PLATFORM_ADMIN',
     '平台管理员',
     'Day-to-day platform operations: tenant management, admin management, audit.',
     'SYSTEM', 'PLATFORM', 'ACTIVE', 20,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

    -- SUPPORT_ADMIN: customer support, read-only + password change
    ('role-platform-support-admin',
     'SUPPORT_ADMIN',
     '平台支持管理员',
     'Customer support role: read tenants, read admins, view audit logs.',
     'SYSTEM', 'PLATFORM', 'ACTIVE', 30,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

    -- AUDITOR: read-only compliance monitoring
    ('role-platform-auditor',
     'AUDITOR',
     '平台审计员',
     'Read-only compliance monitoring: read audit logs and basic platform data.',
     'SYSTEM', 'PLATFORM', 'ACTIVE', 40,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)

ON CONFLICT (code, tenant_id) DO NOTHING;


-- =========================================================
-- 2. Platform Permission Codes (auth_permission)
-- =========================================================
-- Each code matches the Java constant in PlatformPermissions.java.
-- module = 'platform-admin' for all platform-admin managed permissions.

INSERT INTO auth_permission (id, code, name, description, module, type, status, sort_order,
                             tenant_id, created_at, updated_at, deleted)
VALUES
    -- PlatformPermissions.BYPASS_PERMISSION_CHECK = "platform:bypass"
    ('perm-platform-bypass',
     'platform:bypass',
     '权限旁路',
     'Bypass all fine-grained permission checks. Held by SUPER_ADMIN and PLATFORM_ADMIN only.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 1,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

    -- PlatformPermissions.TENANT_READ = "platform:tenant:read"
    ('perm-platform-tenant-read',
     'platform:tenant:read',
     '查看租户列表',
     'Read platform-scoped tenant list and details.',
     'platform-admin', 'BUTTON', 'ACTIVE', 10,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

    -- PlatformPermissions.TENANT_UPDATE_STATUS = "platform:tenant:update-status"
    ('perm-platform-tenant-update-status',
     'platform:tenant:update-status',
     '变更租户状态',
     'Change tenant lifecycle status (ACTIVE/SUSPENDED).',
     'platform-admin', 'BUTTON', 'ACTIVE', 11,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

    -- PlatformPermissions.ADMIN_READ = "platform:admin:read"
    ('perm-platform-admin-read',
     'platform:admin:read',
     '查看超管列表',
     'Read platform administrator list and details.',
     'platform-admin', 'BUTTON', 'ACTIVE', 20,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

    -- PlatformPermissions.ADMIN_CREATE = "platform:admin:create"
    ('perm-platform-admin-create',
     'platform:admin:create',
     '新增超管',
     'Create a new platform administrator account.',
     'platform-admin', 'BUTTON', 'ACTIVE', 21,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

    -- PlatformPermissions.ADMIN_DISABLE = "platform:admin:disable"
    ('perm-platform-admin-disable',
     'platform:admin:disable',
     '禁用超管',
     'Disable an existing platform administrator account.',
     'platform-admin', 'BUTTON', 'ACTIVE', 22,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

    -- PlatformPermissions.ADMIN_RESET_PASSWORD = "platform:admin:reset-password"
    ('perm-platform-admin-reset-password',
     'platform:admin:reset-password',
     '重置超管密码',
     'Reset password of another platform administrator (issues temporary password).',
     'platform-admin', 'BUTTON', 'ACTIVE', 23,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

    -- PlatformPermissions.ADMIN_CHANGE_OWN_PASSWORD = "platform:admin:change-own-password"
    ('perm-platform-admin-change-own-password',
     'platform:admin:change-own-password',
     '修改自己密码',
     'Change the caller own password (all platform admin roles).',
     'platform-admin', 'BUTTON', 'ACTIVE', 24,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

    -- PlatformPermissions.AUDIT_READ = "platform:audit:read"
    ('perm-platform-audit-read',
     'platform:audit:read',
     '查看审计日志',
     'Read platform audit logs with pagination and filtering.',
     'platform-admin', 'BUTTON', 'ACTIVE', 30,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

    -- PlatformPermissions.SYSTEM_CONFIG = "platform:system:config"
    ('perm-platform-system-config',
     'platform:system:config',
     '系统配置',
     'Access and modify system-level configuration. SUPER_ADMIN only.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 40,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

    -- PlatformPermissions.DATA_RECOVERY = "platform:data:recovery"
    ('perm-platform-data-recovery',
     'platform:data:recovery',
     '数据恢复',
     'Perform data recovery and maintenance operations. SUPER_ADMIN only.',
     'platform-admin', 'SYSTEM', 'ACTIVE', 41,
     'PLATFORM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)

ON CONFLICT (code, tenant_id) DO NOTHING;


-- =========================================================
-- 3. Role → Permission Bindings (auth_role_permission)
-- =========================================================

-- ---- SUPER_ADMIN: bypass + all permissions ----
INSERT INTO auth_role_permission (id, role_id, permission_id, tenant_id,
                                  created_at, updated_at, deleted)
SELECT
    CONCAT('rp-super-admin-', p.id),
    'role-platform-super-admin',
    p.id,
    'PLATFORM',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
FROM auth_permission p
WHERE p.tenant_id = 'PLATFORM'
  AND p.deleted = FALSE
ON CONFLICT (role_id, permission_id, tenant_id) DO NOTHING;


-- ---- PLATFORM_ADMIN: bypass + tenant + admin (no system config / data recovery) ----
INSERT INTO auth_role_permission (id, role_id, permission_id, tenant_id,
                                  created_at, updated_at, deleted)
SELECT
    CONCAT('rp-platform-admin-', p.id),
    'role-platform-admin',
    p.id,
    'PLATFORM',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
FROM auth_permission p
WHERE p.tenant_id = 'PLATFORM'
  AND p.deleted = FALSE
  AND p.code NOT IN ('platform:system:config', 'platform:data:recovery')
ON CONFLICT (role_id, permission_id, tenant_id) DO NOTHING;


-- ---- SUPPORT_ADMIN: read-only + audit + own-password-change ----
INSERT INTO auth_role_permission (id, role_id, permission_id, tenant_id,
                                  created_at, updated_at, deleted)
SELECT
    CONCAT('rp-support-admin-', p.id),
    'role-platform-support-admin',
    p.id,
    'PLATFORM',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
FROM auth_permission p
WHERE p.tenant_id = 'PLATFORM'
  AND p.deleted = FALSE
  AND p.code IN (
    'platform:tenant:read',
    'platform:admin:read',
    'platform:admin:change-own-password',
    'platform:audit:read'
  )
ON CONFLICT (role_id, permission_id, tenant_id) DO NOTHING;


-- ---- AUDITOR: read-only audit + own-password-change ----
INSERT INTO auth_role_permission (id, role_id, permission_id, tenant_id,
                                  created_at, updated_at, deleted)
SELECT
    CONCAT('rp-auditor-', p.id),
    'role-platform-auditor',
    p.id,
    'PLATFORM',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
FROM auth_permission p
WHERE p.tenant_id = 'PLATFORM'
  AND p.deleted = FALSE
  AND p.code IN (
    'platform:tenant:read',
    'platform:admin:read',
    'platform:admin:change-own-password',
    'platform:audit:read'
  )
ON CONFLICT (role_id, permission_id, tenant_id) DO NOTHING;
