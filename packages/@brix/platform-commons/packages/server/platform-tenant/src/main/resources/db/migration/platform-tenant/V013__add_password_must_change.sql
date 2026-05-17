-- =============================================================================
-- V013: 强制下次登录修改密码标志
--
-- 蓝图依据:
--   v3.0.9 §安全红线 — Bootstrap / 管理员重置 / 密码到期场景必须强制轮换。
--   行业基线: Auth0 / Keycloak / GitHub Enterprise / Azure AD 均提供
--   "force password change at next sign-in" 标志位。
--
-- 变更:
--   * 新增 password_must_change BOOLEAN NOT NULL DEFAULT FALSE
--   * 新增部分索引 (仅索引 TRUE 行) — 普通查询不付出索引代价，
--     运维查询"待轮换账号"时仍可走索引。
--
-- 兼容性:
--   * 纯加列 + 默认值 FALSE，对存量记录与既有查询零影响。
--   * 不修改 canLogin 语义 (仍由 status + email_verified 决定)，
--     该标志仅由认证响应层 (LoginResponse.mustChangePassword) 驱动 UI 流程。
-- =============================================================================

ALTER TABLE sys_identity
    ADD COLUMN password_must_change BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_sys_identity_password_must_change
    ON sys_identity(password_must_change)
    WHERE password_must_change = TRUE;

COMMENT ON COLUMN sys_identity.password_must_change IS
    '强制下次登录修改密码 (Bootstrap / 管理员重置 / 到期轮换场景置 TRUE，用户成功改密后置 FALSE)';
