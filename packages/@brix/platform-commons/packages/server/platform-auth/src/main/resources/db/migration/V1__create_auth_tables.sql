-- ============================================================================
-- Flyway Migration: V1__create_auth_tables.sql
-- Module: platform-auth (Layer 2.5)
-- Version: 3.0.0
-- Date: 2026-02-12
--
-- 设计原则：
-- 1. 每表 8 个预留字段（ext_varchar_1~5, ext_int_1, ext_json_1, ext_timestamp_1）
-- 2. 软删除（deleted + deleted_at）
-- 3. 统一审计字段（created_at, updated_at, created_by, updated_by）
-- 4. 多租户（tenant_id）
-- 5. VARCHAR(64) 主键（应用层生成）
--
-- 说明：
-- 此脚本为鉴权域（auth_*）的全部表结构。
-- 身份域（identity_*）表由 shinwa-app-identity 模块的 V2 迁移创建。
-- auth_social_connection 已迁入身份域，改为 identity_social_connection。
-- ============================================================================


-- ============================================================================
-- 1. 用户表 (auth_user)
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_user (
    -- 主键
    id                  VARCHAR(64)     NOT NULL PRIMARY KEY,

    -- 基本信息
    username            VARCHAR(100)    NOT NULL,
    email               VARCHAR(255),
    phone               VARCHAR(32),
    password_hash       VARCHAR(255),
    display_name        VARCHAR(100),
    avatar_url          VARCHAR(512),

    -- 状态
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    email_verified      BOOLEAN         NOT NULL DEFAULT FALSE,
    phone_verified      BOOLEAN         NOT NULL DEFAULT FALSE,

    -- 安全
    failed_login_count  INTEGER         NOT NULL DEFAULT 0,
    locked_until        TIMESTAMP,
    password_changed_at TIMESTAMP,
    last_login_at       TIMESTAMP,
    last_login_ip       VARCHAR(64),

    -- 便捷角色（非正式 RBAC，用于 JWT 签发快速读取）
    role                VARCHAR(32)     NOT NULL DEFAULT 'USER',

    -- 多租户
    tenant_id           VARCHAR(64)     NOT NULL,

    -- 审计字段
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,

    -- 预留字段（8 个）
    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP,

    -- 约束
    CONSTRAINT uk_auth_user_username_tenant UNIQUE (username, tenant_id),
    CONSTRAINT uk_auth_user_email_tenant UNIQUE (email, tenant_id),
    CONSTRAINT uk_auth_user_phone_tenant UNIQUE (phone, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_auth_user_tenant ON auth_user(tenant_id);
CREATE INDEX IF NOT EXISTS idx_auth_user_status ON auth_user(status) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_user_email  ON auth_user(email)  WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_user_phone  ON auth_user(phone)  WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_user_role   ON auth_user(role)   WHERE deleted = FALSE;

COMMENT ON TABLE auth_user IS '用户认证表 — 最小认证记录';
COMMENT ON COLUMN auth_user.role IS '便捷角色列（JWT 签发用），正式 RBAC 走 auth_user_role';


-- ============================================================================
-- 2. 角色表 (auth_role)
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_role (
    id                  VARCHAR(64)     NOT NULL PRIMARY KEY,

    code                VARCHAR(100)    NOT NULL,
    name                VARCHAR(100)    NOT NULL,
    description         VARCHAR(500),

    type                VARCHAR(32)     NOT NULL DEFAULT 'CUSTOM',
    scope               VARCHAR(32)     NOT NULL DEFAULT 'GLOBAL',
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',

    parent_id           VARCHAR(64),
    sort_order          INTEGER         NOT NULL DEFAULT 0,

    tenant_id           VARCHAR(64)     NOT NULL,

    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,

    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP,

    CONSTRAINT uk_auth_role_code_tenant UNIQUE (code, tenant_id),
    CONSTRAINT fk_auth_role_parent FOREIGN KEY (parent_id) REFERENCES auth_role(id)
);

CREATE INDEX IF NOT EXISTS idx_auth_role_tenant ON auth_role(tenant_id);
CREATE INDEX IF NOT EXISTS idx_auth_role_parent ON auth_role(parent_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_role_type   ON auth_role(type)      WHERE deleted = FALSE;

COMMENT ON TABLE auth_role IS '角色表 — 系统 / 自定义角色定义';


-- ============================================================================
-- 3. 权限表 (auth_permission)
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_permission (
    id                  VARCHAR(64)     NOT NULL PRIMARY KEY,

    code                VARCHAR(200)    NOT NULL,
    name                VARCHAR(100)    NOT NULL,
    description         VARCHAR(500),

    module              VARCHAR(100)    NOT NULL,
    type                VARCHAR(32)     NOT NULL,

    http_method         VARCHAR(16),
    api_path            VARCHAR(500),

    menu_path           VARCHAR(500),
    menu_icon           VARCHAR(100),

    parent_id           VARCHAR(64),
    sort_order          INTEGER         NOT NULL DEFAULT 0,

    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',

    tenant_id           VARCHAR(64)     NOT NULL,

    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,

    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP,

    CONSTRAINT uk_auth_permission_code_tenant UNIQUE (code, tenant_id),
    CONSTRAINT fk_auth_permission_parent FOREIGN KEY (parent_id) REFERENCES auth_permission(id)
);

CREATE INDEX IF NOT EXISTS idx_auth_permission_tenant ON auth_permission(tenant_id);
CREATE INDEX IF NOT EXISTS idx_auth_permission_module ON auth_permission(module) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_permission_type   ON auth_permission(type)   WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_permission_parent ON auth_permission(parent_id) WHERE deleted = FALSE;

COMMENT ON TABLE auth_permission IS '权限表 — MENU / BUTTON / API / DATA';


-- ============================================================================
-- 4. 用户角色关联表 (auth_user_role)
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_user_role (
    id                  VARCHAR(64)     NOT NULL PRIMARY KEY,

    user_id             VARCHAR(64)     NOT NULL,
    role_id             VARCHAR(64)     NOT NULL,

    valid_from          TIMESTAMP,
    valid_until         TIMESTAMP,

    tenant_id           VARCHAR(64)     NOT NULL,

    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
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

CREATE INDEX IF NOT EXISTS idx_auth_user_role_user   ON auth_user_role(user_id)   WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_user_role_role   ON auth_user_role(role_id)   WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_user_role_tenant ON auth_user_role(tenant_id);

COMMENT ON TABLE auth_user_role IS '用户角色关联表';


-- ============================================================================
-- 5. 角色权限关联表 (auth_role_permission)
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_role_permission (
    id                  VARCHAR(64)     NOT NULL PRIMARY KEY,

    role_id             VARCHAR(64)     NOT NULL,
    permission_id       VARCHAR(64)     NOT NULL,

    data_scope          VARCHAR(32),
    data_scope_custom   JSONB,

    tenant_id           VARCHAR(64)     NOT NULL,

    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
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
    CONSTRAINT fk_auth_role_permission_perm FOREIGN KEY (permission_id) REFERENCES auth_permission(id)
);

CREATE INDEX IF NOT EXISTS idx_auth_role_perm_role   ON auth_role_permission(role_id)      WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_role_perm_perm   ON auth_role_permission(permission_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_role_perm_tenant ON auth_role_permission(tenant_id);

COMMENT ON TABLE auth_role_permission IS '角色权限关联表';


-- ============================================================================
-- 6. 会话表 (auth_session)
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_session (
    id                  VARCHAR(64)     NOT NULL PRIMARY KEY,

    user_id             VARCHAR(64)     NOT NULL,

    token_hash          VARCHAR(255)    NOT NULL,
    refresh_token_hash  VARCHAR(255),

    device_id           VARCHAR(128),
    device_type         VARCHAR(32),
    device_name         VARCHAR(128),
    browser             VARCHAR(128),
    os                  VARCHAR(128),
    ip_address          VARCHAR(64),
    user_agent          VARCHAR(500),

    expires_at          TIMESTAMP       NOT NULL,
    refresh_expires_at  TIMESTAMP,
    last_activity_at    TIMESTAMP,

    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    revoked_at          TIMESTAMP,
    revoked_reason      VARCHAR(200),

    tenant_id           VARCHAR(64)     NOT NULL,

    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
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

CREATE INDEX IF NOT EXISTS idx_auth_session_user    ON auth_session(user_id)    WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_session_token   ON auth_session(token_hash) WHERE deleted = FALSE AND status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_auth_session_tenant  ON auth_session(tenant_id);
CREATE INDEX IF NOT EXISTS idx_auth_session_expires ON auth_session(expires_at) WHERE deleted = FALSE AND status = 'ACTIVE';

COMMENT ON TABLE auth_session IS '会话表 — 用户登录 Session 管理';


-- ============================================================================
-- 7. OAuth 客户端表 (auth_oauth_client)
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_oauth_client (
    id                      VARCHAR(64)     NOT NULL PRIMARY KEY,

    client_id               VARCHAR(128)    NOT NULL,
    client_secret_hash      VARCHAR(255),
    client_name             VARCHAR(100)    NOT NULL,
    description             VARCHAR(500),

    grant_types             VARCHAR(500)    NOT NULL,
    redirect_uris           TEXT,
    scopes                  VARCHAR(1000),

    access_token_ttl        INTEGER         NOT NULL DEFAULT 3600,
    refresh_token_ttl       INTEGER         NOT NULL DEFAULT 604800,
    require_pkce            BOOLEAN         NOT NULL DEFAULT TRUE,
    require_consent         BOOLEAN         NOT NULL DEFAULT FALSE,

    status                  VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',

    tenant_id               VARCHAR(64)     NOT NULL,

    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              VARCHAR(64),
    updated_by              VARCHAR(64),
    deleted                 BOOLEAN         NOT NULL DEFAULT FALSE,
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

COMMENT ON TABLE auth_oauth_client IS 'OAuth 客户端表';


-- ============================================================================
-- 8. 操作审计日志表 (auth_audit_log)
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_audit_log (
    id                  VARCHAR(64)     NOT NULL PRIMARY KEY,

    event_type          VARCHAR(64)     NOT NULL,
    event_status        VARCHAR(32)     NOT NULL,

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

    tenant_id           VARCHAR(64)     NOT NULL,

    -- 审计日志为只追加
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auth_audit_user   ON auth_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_audit_event  ON auth_audit_log(event_type);
CREATE INDEX IF NOT EXISTS idx_auth_audit_tenant ON auth_audit_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_auth_audit_time   ON auth_audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_auth_audit_target ON auth_audit_log(target_type, target_id);

COMMENT ON TABLE auth_audit_log IS '操作审计日志表 — 只追加';


-- ============================================================================
-- 初始化种子数据
-- ============================================================================
INSERT INTO auth_role (id, code, name, description, type, scope, status, tenant_id, sort_order)
VALUES
    ('role-super-admin',  'SUPER_ADMIN',  '超级管理员', '系统超级管理员，拥有所有权限', 'SYSTEM', 'GLOBAL', 'ACTIVE', 'default', 1),
    ('role-tenant-admin', 'TENANT_ADMIN', '租户管理员', '租户级别管理员',             'SYSTEM', 'TENANT', 'ACTIVE', 'default', 2),
    ('role-user',         'USER',         '普通用户',   '普通注册用户',               'SYSTEM', 'TENANT', 'ACTIVE', 'default', 3)
ON CONFLICT (code, tenant_id) DO NOTHING;


-- ============================================================================
-- 表结构摘要
-- ============================================================================
--
-- | #  | 表名                      | 说明               | 预留字段 |
-- |----|--------------------------|--------------------|---------| 
-- | 1  | auth_user                | 用户认证表          | 8       |
-- | 2  | auth_role                | 角色表              | 8       |
-- | 3  | auth_permission          | 权限表              | 8       |
-- | 4  | auth_user_role           | 用户角色关联表       | 8       |
-- | 5  | auth_role_permission     | 角色权限关联表       | 8       |
-- | 6  | auth_session             | 会话表              | 8       |
-- | 7  | auth_oauth_client        | OAuth 客户端表      | 8       |
-- | 8  | auth_audit_log           | 操作审计日志表       | 8       |
--
-- 注意：auth_social_connection 已迁入身份域 → identity_social_connection
-- ============================================================================
