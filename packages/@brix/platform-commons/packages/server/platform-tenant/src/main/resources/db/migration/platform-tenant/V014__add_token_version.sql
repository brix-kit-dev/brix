-- =============================================================================
-- V014: 添加 token_version 列（A3 — 令牌版本校验安全基线）
--
-- 蓝图依据:
--   v3.0.9 §安全红线 — 密码修改场景需吊销已颁发令牌。
--   行业基线: Auth0 / Keycloak / GitHub Enterprise 均以递增版本号实现
--   按用户的令牌批量失效。
--
-- 机制:
--   * JWT 颁发时将 sys_identity.token_version 写入 tv claim。
--   * 每次密码修改后 token_version += 1。
--   * SecurityContextFilter 在每次请求时对比 JWT.tv 与 DB 值，
--     tv < DB 值则拒绝（令牌已失效）。
--
-- 兼容性:
--   * DEFAULT 1 — 存量账号在下次密码修改前仍可正常使用（版本 ≥ 1 即合法）。
--   * 纯加列，不影响既有查询。
-- =============================================================================

ALTER TABLE sys_identity
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 1;

COMMENT ON COLUMN sys_identity.token_version IS
    '令牌版本号 (A3 安全基线): 每次密码修改递增 1，使所有旧令牌失效';
