-- =============================================================================
-- V2: 创建 auth_refresh_token 表（A2 — Refresh Token 持久化与吊销）
--
-- 蓝图依据:
--   v3.0.9 §安全红线 — 会话令牌必须可吊销（密码修改 / 强制局放场景）。
--   行业基线: Auth0 / Google OAuth2 / Azure AD 均使用有状态 Refresh Token。
--
-- 机制:
--   * 每次颁发 Refresh Token 时插入一条记录。
--   * 使用时旋转（旧记录 revoked_at 置非 NULL，写入新记录）。
--   * 密码修改后 revokeAllByIdentityId() 批量吊销。
--   * 定期清理过期记录（revoked_at 不为空 OR expires_at < NOW()）。
--
-- 字段说明:
--   * token_id   — 不透明 UUID，客户端存储并提交的令牌值
--   * identity_id / admin_id — 关联身份（admin_id 仅平台管理员使用）
--   * revoked_at — 吊销时间戳（NULL = 有效；非 NULL = 已吊销）
-- =============================================================================

CREATE TABLE IF NOT EXISTS auth_refresh_token (
    id              BIGINT          NOT NULL PRIMARY KEY,
    token_id        VARCHAR(64)     NOT NULL,
    identity_id     BIGINT          NOT NULL,
    admin_id        BIGINT,
    issued_at       TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMPTZ     NOT NULL,
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

COMMENT ON TABLE auth_refresh_token IS 'Refresh Token 持久化存储 — 支持吊销与旋转 (A2 安全基线)';
COMMENT ON COLUMN auth_refresh_token.token_id IS '不透明令牌标识（UUID v4），存储在客户端 Cookie/localStorage';
COMMENT ON COLUMN auth_refresh_token.identity_id IS '关联的全局身份 ID';
COMMENT ON COLUMN auth_refresh_token.admin_id IS '平台管理员 ID（非平台管理员为 NULL）';
COMMENT ON COLUMN auth_refresh_token.revoked_at IS 'NULL = 有效；非 NULL = 已吊销（密码修改 / 主动登出）';
