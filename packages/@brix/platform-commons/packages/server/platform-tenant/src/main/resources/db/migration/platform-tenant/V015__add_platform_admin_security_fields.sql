-- =============================================================================
-- V015: 扩展 sys_platform_admin 与 sys_identity 安全字段
--
-- 蓝图依据:
--   v1.0-平台超管最小实现-唯一真相来源.md §3.2 (新增字段 V012)
--   命名为 V015 是因为 V012-V014 已分别用于配额、强制改密、令牌版本字段。
--
-- 变更内容:
--   sys_platform_admin:
--     * created_by              — 创建此超管账户的操作者 identity_id；首个 bootstrap 时为 NULL
--     * disabled_at             — 账户被禁用的时间戳
--     * disabled_by             — 执行禁用操作的操作者 identity_id
--     * disable_reason          — 禁用原因（可选备注）
--     * temp_password_expires_at — 临时密码过期时间；登录时服务端校验 < now()
--
--   sys_identity:
--     * failed_login_count      — 连续登录失败次数；达到阈值后触发临时锁定
--     * locked_until            — 临时锁定截止时间；NULL 表示未锁定
--     * last_login_ip           — 最后一次成功登录的客户端 IP（IPv4/IPv6）
--
-- 兼容性:
--   * 全部为纯加列操作，默认值安全，对存量行为零影响。
--   * temp_password_expires_at 仅在重置密码流程后置值，首次创建为 NULL 即合法。
--
-- 安全红线:
--   * disable_reason 禁止写入任何密码、token 或 secret 内容（SSOT §10 R-10）。
--   * last_login_ip 内容仅限于审计目的，不得用于身份判断。
-- =============================================================================

-- =========================================================
-- sys_platform_admin 扩展字段
-- =========================================================

ALTER TABLE sys_platform_admin
    -- Who created this admin record (NULL for the first bootstrap admin).
    ADD COLUMN IF NOT EXISTS created_by BIGINT;

ALTER TABLE sys_platform_admin
    -- Timestamp when this admin account was disabled.
    ADD COLUMN IF NOT EXISTS disabled_at TIMESTAMPTZ;

ALTER TABLE sys_platform_admin
    -- identity_id of the admin who performed the disable operation.
    ADD COLUMN IF NOT EXISTS disabled_by BIGINT;

ALTER TABLE sys_platform_admin
    -- Optional textual reason for disabling the account (operator-supplied).
    -- SECURITY: MUST NOT contain passwords, tokens, or secrets.
    ADD COLUMN IF NOT EXISTS disable_reason VARCHAR(512);

ALTER TABLE sys_platform_admin
    -- Expiry timestamp for a temporary (one-time) password.
    -- NULL means no temporary password is currently in effect.
    -- On first login after reset, the service sets this to NULL immediately.
    ADD COLUMN IF NOT EXISTS temp_password_expires_at TIMESTAMPTZ;

-- Index on created_by to support queries like "who did this admin create?"
CREATE INDEX IF NOT EXISTS idx_sys_platform_admin_created_by
    ON sys_platform_admin(created_by);

COMMENT ON COLUMN sys_platform_admin.created_by IS
    '创建此超管账户的操作者 identity_id；首个 bootstrap 超管时为 NULL';
COMMENT ON COLUMN sys_platform_admin.disabled_at IS
    '账户被禁用的时间戳；NULL 表示账户当前为 ACTIVE';
COMMENT ON COLUMN sys_platform_admin.disabled_by IS
    '执行禁用操作的操作者 identity_id';
COMMENT ON COLUMN sys_platform_admin.disable_reason IS
    '禁用原因备注（安全要求：禁止写入密码/token/secret）';
COMMENT ON COLUMN sys_platform_admin.temp_password_expires_at IS
    '临时密码过期时间；登录时服务端校验 < now()；登录后立即置 NULL';

-- =========================================================
-- sys_identity 登录安全字段
-- =========================================================

ALTER TABLE sys_identity
    -- Consecutive failed login attempts. Reset to 0 on successful login.
    -- Used to implement the S3 lockout policy (5 failures → 15 min lock).
    ADD COLUMN IF NOT EXISTS failed_login_count INT NOT NULL DEFAULT 0;

ALTER TABLE sys_identity
    -- Temporary account lockout deadline. NULL = not locked.
    -- Checked before password verification; if now() < locked_until, reject immediately.
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;

ALTER TABLE sys_identity
    -- Last successful login IP address (IPv4 or IPv6, up to 64 chars).
    -- Purely for audit / anomaly detection — not used for authorization.
    ADD COLUMN IF NOT EXISTS last_login_ip VARCHAR(64);

-- Partial index: only indexes rows that are currently locked, keeping the index small.
CREATE INDEX IF NOT EXISTS idx_sys_identity_locked_until
    ON sys_identity(locked_until)
    WHERE locked_until IS NOT NULL;

COMMENT ON COLUMN sys_identity.failed_login_count IS
    '连续登录失败次数；成功登录后归零；达到阈值触发临时锁定 (security S3)';
COMMENT ON COLUMN sys_identity.locked_until IS
    '临时锁定截止时间；NULL 表示未锁定；锁定期间直接拒绝登录 (security S3)';
COMMENT ON COLUMN sys_identity.last_login_ip IS
    '最后一次成功登录的客户端 IP (IPv4/IPv6)；仅用于审计，不参与鉴权';
