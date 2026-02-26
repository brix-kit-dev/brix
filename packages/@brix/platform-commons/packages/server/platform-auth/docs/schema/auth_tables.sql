-- ============================================================================
-- Shinwa Platform v3.0 认证模块数据库表结构设计
-- ============================================================================
-- 数据库：PostgreSQL / Kingbase（PostgreSQL 兼容模式）
-- 版本：3.0.0
-- 创建日期：2026-02-03
-- 
-- 设计原则：
-- 1. 每个表包含 8 个预留字段，支持业务扩展
-- 2. 采用软删除策略（deleted 标记）
-- 3. 统一的审计字段（created_at, updated_at, created_by, updated_by）
-- 4. 支持多租户架构（tenant_id）
-- ============================================================================

-- ============================================================================
-- 1. 用户表 (auth_user)
-- ============================================================================
-- 存储用户基本信息和认证凭据
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
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE, LOCKED, PENDING
    email_verified      BOOLEAN         NOT NULL DEFAULT FALSE,
    phone_verified      BOOLEAN         NOT NULL DEFAULT FALSE,
    
    -- 安全
    failed_login_count  INTEGER         NOT NULL DEFAULT 0,
    locked_until        TIMESTAMP,
    password_changed_at TIMESTAMP,
    last_login_at       TIMESTAMP,
    last_login_ip       VARCHAR(64),
    
    -- 多租户
    tenant_id           VARCHAR(64)     NOT NULL,
    
    -- 审计字段
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    
    -- 预留字段（8个）
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

-- 索引
CREATE INDEX idx_auth_user_tenant ON auth_user(tenant_id);
CREATE INDEX idx_auth_user_status ON auth_user(status) WHERE deleted = FALSE;
CREATE INDEX idx_auth_user_email ON auth_user(email) WHERE deleted = FALSE;
CREATE INDEX idx_auth_user_phone ON auth_user(phone) WHERE deleted = FALSE;

COMMENT ON TABLE auth_user IS '用户表 - 存储用户基本信息和认证凭据';
COMMENT ON COLUMN auth_user.ext_varchar_1 IS '预留字段1（字符串）';
COMMENT ON COLUMN auth_user.ext_varchar_2 IS '预留字段2（字符串）';
COMMENT ON COLUMN auth_user.ext_varchar_3 IS '预留字段3（字符串）';
COMMENT ON COLUMN auth_user.ext_varchar_4 IS '预留字段4（字符串）';
COMMENT ON COLUMN auth_user.ext_varchar_5 IS '预留字段5（字符串）';
COMMENT ON COLUMN auth_user.ext_int_1 IS '预留字段6（整数）';
COMMENT ON COLUMN auth_user.ext_json_1 IS '预留字段7（JSON）';
COMMENT ON COLUMN auth_user.ext_timestamp_1 IS '预留字段8（时间戳）';


-- ============================================================================
-- 2. 角色表 (auth_role)
-- ============================================================================
-- 存储系统角色定义
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_role (
    -- 主键
    id                  VARCHAR(64)     NOT NULL PRIMARY KEY,
    
    -- 基本信息
    code                VARCHAR(100)    NOT NULL,
    name                VARCHAR(100)    NOT NULL,
    description         VARCHAR(500),
    
    -- 分类
    type                VARCHAR(32)     NOT NULL DEFAULT 'CUSTOM',  -- SYSTEM, CUSTOM
    scope               VARCHAR(32)     NOT NULL DEFAULT 'GLOBAL',  -- GLOBAL, TENANT
    
    -- 状态
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE
    
    -- 层级（支持角色继承）
    parent_id           VARCHAR(64),
    sort_order          INTEGER         NOT NULL DEFAULT 0,
    
    -- 多租户
    tenant_id           VARCHAR(64)     NOT NULL,
    
    -- 审计字段
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    
    -- 预留字段（8个）
    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP,
    
    -- 约束
    CONSTRAINT uk_auth_role_code_tenant UNIQUE (code, tenant_id),
    CONSTRAINT fk_auth_role_parent FOREIGN KEY (parent_id) REFERENCES auth_role(id)
);

-- 索引
CREATE INDEX idx_auth_role_tenant ON auth_role(tenant_id);
CREATE INDEX idx_auth_role_parent ON auth_role(parent_id) WHERE deleted = FALSE;
CREATE INDEX idx_auth_role_type ON auth_role(type) WHERE deleted = FALSE;

COMMENT ON TABLE auth_role IS '角色表 - 存储系统角色定义';


-- ============================================================================
-- 3. 权限表 (auth_permission)
-- ============================================================================
-- 存储系统权限定义
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_permission (
    -- 主键
    id                  VARCHAR(64)     NOT NULL PRIMARY KEY,
    
    -- 基本信息
    code                VARCHAR(200)    NOT NULL,
    name                VARCHAR(100)    NOT NULL,
    description         VARCHAR(500),
    
    -- 分类
    module              VARCHAR(100)    NOT NULL,       -- 所属模块
    type                VARCHAR(32)     NOT NULL,       -- MENU, BUTTON, API, DATA
    
    -- API 权限扩展
    http_method         VARCHAR(16),                    -- GET, POST, PUT, DELETE
    api_path            VARCHAR(500),                   -- API 路径模式
    
    -- 菜单权限扩展
    menu_path           VARCHAR(500),                   -- 前端路由路径
    menu_icon           VARCHAR(100),                   -- 菜单图标
    
    -- 层级
    parent_id           VARCHAR(64),
    sort_order          INTEGER         NOT NULL DEFAULT 0,
    
    -- 状态
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    
    -- 多租户
    tenant_id           VARCHAR(64)     NOT NULL,
    
    -- 审计字段
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    
    -- 预留字段（8个）
    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP,
    
    -- 约束
    CONSTRAINT uk_auth_permission_code_tenant UNIQUE (code, tenant_id),
    CONSTRAINT fk_auth_permission_parent FOREIGN KEY (parent_id) REFERENCES auth_permission(id)
);

-- 索引
CREATE INDEX idx_auth_permission_tenant ON auth_permission(tenant_id);
CREATE INDEX idx_auth_permission_module ON auth_permission(module) WHERE deleted = FALSE;
CREATE INDEX idx_auth_permission_type ON auth_permission(type) WHERE deleted = FALSE;
CREATE INDEX idx_auth_permission_parent ON auth_permission(parent_id) WHERE deleted = FALSE;

COMMENT ON TABLE auth_permission IS '权限表 - 存储系统权限定义';


-- ============================================================================
-- 4. 用户角色关联表 (auth_user_role)
-- ============================================================================
-- 存储用户与角色的多对多关系
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_user_role (
    -- 主键
    id                  VARCHAR(64)     NOT NULL PRIMARY KEY,
    
    -- 关联
    user_id             VARCHAR(64)     NOT NULL,
    role_id             VARCHAR(64)     NOT NULL,
    
    -- 有效期
    valid_from          TIMESTAMP,
    valid_until         TIMESTAMP,
    
    -- 多租户
    tenant_id           VARCHAR(64)     NOT NULL,
    
    -- 审计字段
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    
    -- 预留字段（8个）
    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP,
    
    -- 约束
    CONSTRAINT uk_auth_user_role UNIQUE (user_id, role_id, tenant_id),
    CONSTRAINT fk_auth_user_role_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
    CONSTRAINT fk_auth_user_role_role FOREIGN KEY (role_id) REFERENCES auth_role(id)
);

-- 索引
CREATE INDEX idx_auth_user_role_user ON auth_user_role(user_id) WHERE deleted = FALSE;
CREATE INDEX idx_auth_user_role_role ON auth_user_role(role_id) WHERE deleted = FALSE;
CREATE INDEX idx_auth_user_role_tenant ON auth_user_role(tenant_id);

COMMENT ON TABLE auth_user_role IS '用户角色关联表 - 存储用户与角色的多对多关系';


-- ============================================================================
-- 5. 角色权限关联表 (auth_role_permission)
-- ============================================================================
-- 存储角色与权限的多对多关系
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_role_permission (
    -- 主键
    id                  VARCHAR(64)     NOT NULL PRIMARY KEY,
    
    -- 关联
    role_id             VARCHAR(64)     NOT NULL,
    permission_id       VARCHAR(64)     NOT NULL,
    
    -- 权限范围
    data_scope          VARCHAR(32),            -- ALL, TENANT, DEPT, SELF
    data_scope_custom   JSONB,                  -- 自定义数据范围
    
    -- 多租户
    tenant_id           VARCHAR(64)     NOT NULL,
    
    -- 审计字段
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    
    -- 预留字段（8个）
    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP,
    
    -- 约束
    CONSTRAINT uk_auth_role_permission UNIQUE (role_id, permission_id, tenant_id),
    CONSTRAINT fk_auth_role_permission_role FOREIGN KEY (role_id) REFERENCES auth_role(id),
    CONSTRAINT fk_auth_role_permission_perm FOREIGN KEY (permission_id) REFERENCES auth_permission(id)
);

-- 索引
CREATE INDEX idx_auth_role_permission_role ON auth_role_permission(role_id) WHERE deleted = FALSE;
CREATE INDEX idx_auth_role_permission_perm ON auth_role_permission(permission_id) WHERE deleted = FALSE;
CREATE INDEX idx_auth_role_permission_tenant ON auth_role_permission(tenant_id);

COMMENT ON TABLE auth_role_permission IS '角色权限关联表 - 存储角色与权限的多对多关系';


-- ============================================================================
-- 6. 会话表 (auth_session)
-- ============================================================================
-- 存储用户登录会话信息
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_session (
    -- 主键
    id                  VARCHAR(64)     NOT NULL PRIMARY KEY,
    
    -- 关联
    user_id             VARCHAR(64)     NOT NULL,
    
    -- 会话信息
    token_hash          VARCHAR(255)    NOT NULL,
    refresh_token_hash  VARCHAR(255),
    
    -- 客户端信息
    device_id           VARCHAR(128),
    device_type         VARCHAR(32),            -- WEB, MOBILE, TABLET, DESKTOP
    device_name         VARCHAR(128),
    browser             VARCHAR(128),
    os                  VARCHAR(128),
    ip_address          VARCHAR(64),
    user_agent          VARCHAR(500),
    
    -- 有效期
    expires_at          TIMESTAMP       NOT NULL,
    refresh_expires_at  TIMESTAMP,
    last_activity_at    TIMESTAMP,
    
    -- 状态
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, EXPIRED, REVOKED
    revoked_at          TIMESTAMP,
    revoked_reason      VARCHAR(200),
    
    -- 多租户
    tenant_id           VARCHAR(64)     NOT NULL,
    
    -- 审计字段
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    
    -- 预留字段（8个）
    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP,
    
    -- 约束
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES auth_user(id)
);

-- 索引
CREATE INDEX idx_auth_session_user ON auth_session(user_id) WHERE deleted = FALSE;
CREATE INDEX idx_auth_session_token ON auth_session(token_hash) WHERE deleted = FALSE AND status = 'ACTIVE';
CREATE INDEX idx_auth_session_tenant ON auth_session(tenant_id);
CREATE INDEX idx_auth_session_expires ON auth_session(expires_at) WHERE deleted = FALSE AND status = 'ACTIVE';

COMMENT ON TABLE auth_session IS '会话表 - 存储用户登录会话信息';


-- ============================================================================
-- 7. OAuth 客户端表 (auth_oauth_client)
-- ============================================================================
-- 存储 OAuth2 客户端配置
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_oauth_client (
    -- 主键
    id                      VARCHAR(64)     NOT NULL PRIMARY KEY,
    
    -- 客户端信息
    client_id               VARCHAR(128)    NOT NULL,
    client_secret_hash      VARCHAR(255),
    client_name             VARCHAR(100)    NOT NULL,
    description             VARCHAR(500),
    
    -- OAuth 配置
    grant_types             VARCHAR(500)    NOT NULL,   -- authorization_code,client_credentials,refresh_token
    redirect_uris           TEXT,                       -- JSON 数组
    scopes                  VARCHAR(1000),              -- read,write,profile
    
    -- 安全配置
    access_token_ttl        INTEGER         NOT NULL DEFAULT 3600,      -- 秒
    refresh_token_ttl       INTEGER         NOT NULL DEFAULT 604800,    -- 秒
    require_pkce            BOOLEAN         NOT NULL DEFAULT TRUE,
    require_consent         BOOLEAN         NOT NULL DEFAULT FALSE,
    
    -- 状态
    status                  VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    
    -- 多租户
    tenant_id               VARCHAR(64)     NOT NULL,
    
    -- 审计字段
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              VARCHAR(64),
    updated_by              VARCHAR(64),
    deleted                 BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    
    -- 预留字段（8个）
    ext_varchar_1           VARCHAR(255),
    ext_varchar_2           VARCHAR(255),
    ext_varchar_3           VARCHAR(255),
    ext_varchar_4           VARCHAR(255),
    ext_varchar_5           VARCHAR(255),
    ext_int_1               INTEGER,
    ext_json_1              JSONB,
    ext_timestamp_1         TIMESTAMP,
    
    -- 约束
    CONSTRAINT uk_auth_oauth_client_id UNIQUE (client_id)
);

-- 索引
CREATE INDEX idx_auth_oauth_client_tenant ON auth_oauth_client(tenant_id);
CREATE INDEX idx_auth_oauth_client_status ON auth_oauth_client(status) WHERE deleted = FALSE;

COMMENT ON TABLE auth_oauth_client IS 'OAuth 客户端表 - 存储 OAuth2 客户端配置';


-- ============================================================================
-- 8. 第三方账号绑定表 (auth_social_connection)
-- ============================================================================
-- 存储用户的第三方账号绑定关系
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_social_connection (
    -- 主键
    id                  VARCHAR(64)     NOT NULL PRIMARY KEY,
    
    -- 关联
    user_id             VARCHAR(64)     NOT NULL,
    
    -- 第三方信息
    provider            VARCHAR(32)     NOT NULL,       -- WECHAT, GOOGLE, GITHUB, DINGTALK
    provider_user_id    VARCHAR(255)    NOT NULL,       -- 第三方用户 ID
    
    -- 用户信息快照
    nickname            VARCHAR(100),
    avatar_url          VARCHAR(512),
    email               VARCHAR(255),
    
    -- Token 信息
    access_token        TEXT,
    refresh_token       TEXT,
    token_expires_at    TIMESTAMP,
    
    -- 附加数据
    raw_data            JSONB,                          -- 原始响应数据
    
    -- 状态
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    bound_at            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at       TIMESTAMP,
    
    -- 多租户
    tenant_id           VARCHAR(64)     NOT NULL,
    
    -- 审计字段
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    
    -- 预留字段（8个）
    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP,
    
    -- 约束
    CONSTRAINT uk_auth_social_provider_user UNIQUE (provider, provider_user_id, tenant_id),
    CONSTRAINT fk_auth_social_user FOREIGN KEY (user_id) REFERENCES auth_user(id)
);

-- 索引
CREATE INDEX idx_auth_social_user ON auth_social_connection(user_id) WHERE deleted = FALSE;
CREATE INDEX idx_auth_social_provider ON auth_social_connection(provider) WHERE deleted = FALSE;
CREATE INDEX idx_auth_social_tenant ON auth_social_connection(tenant_id);

COMMENT ON TABLE auth_social_connection IS '第三方账号绑定表 - 存储用户的第三方账号绑定关系';


-- ============================================================================
-- 9. 操作审计日志表 (auth_audit_log)
-- ============================================================================
-- 存储用户认证相关的操作审计日志
-- ============================================================================
CREATE TABLE IF NOT EXISTS auth_audit_log (
    -- 主键
    id                  VARCHAR(64)     NOT NULL PRIMARY KEY,
    
    -- 操作信息
    event_type          VARCHAR(64)     NOT NULL,       -- LOGIN, LOGOUT, PASSWORD_CHANGE, ROLE_CHANGE 等
    event_status        VARCHAR(32)     NOT NULL,       -- SUCCESS, FAILURE
    
    -- 操作者
    user_id             VARCHAR(64),
    username            VARCHAR(100),
    
    -- 目标
    target_type         VARCHAR(64),                    -- USER, ROLE, PERMISSION, SESSION
    target_id           VARCHAR(64),
    
    -- 详情
    description         VARCHAR(500),
    detail              JSONB,
    
    -- 来源
    ip_address          VARCHAR(64),
    user_agent          VARCHAR(500),
    device_type         VARCHAR(32),
    
    -- 请求信息
    request_id          VARCHAR(64),
    request_path        VARCHAR(500),
    
    -- 多租户
    tenant_id           VARCHAR(64)     NOT NULL,
    
    -- 审计字段（此表为只追加，不需要 update 相关字段）
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 预留字段（8个）
    ext_varchar_1       VARCHAR(255),
    ext_varchar_2       VARCHAR(255),
    ext_varchar_3       VARCHAR(255),
    ext_varchar_4       VARCHAR(255),
    ext_varchar_5       VARCHAR(255),
    ext_int_1           INTEGER,
    ext_json_1          JSONB,
    ext_timestamp_1     TIMESTAMP
);

-- 索引
CREATE INDEX idx_auth_audit_log_user ON auth_audit_log(user_id);
CREATE INDEX idx_auth_audit_log_event ON auth_audit_log(event_type);
CREATE INDEX idx_auth_audit_log_tenant ON auth_audit_log(tenant_id);
CREATE INDEX idx_auth_audit_log_time ON auth_audit_log(created_at);
CREATE INDEX idx_auth_audit_log_target ON auth_audit_log(target_type, target_id);

COMMENT ON TABLE auth_audit_log IS '操作审计日志表 - 存储用户认证相关的操作审计日志';


-- ============================================================================
-- 初始化数据
-- ============================================================================

-- 默认系统角色
INSERT INTO auth_role (id, code, name, description, type, scope, status, tenant_id, sort_order)
VALUES 
    ('role-super-admin', 'SUPER_ADMIN', '超级管理员', '系统超级管理员，拥有所有权限', 'SYSTEM', 'GLOBAL', 'ACTIVE', 'default', 1),
    ('role-tenant-admin', 'TENANT_ADMIN', '租户管理员', '租户级别管理员', 'SYSTEM', 'TENANT', 'ACTIVE', 'default', 2),
    ('role-user', 'USER', '普通用户', '普通注册用户', 'SYSTEM', 'TENANT', 'ACTIVE', 'default', 3)
ON CONFLICT (code, tenant_id) DO NOTHING;


-- ============================================================================
-- 表结构摘要
-- ============================================================================
-- 
-- | 表名                      | 说明               | 预留字段 |
-- |--------------------------|--------------------|---------| 
-- | auth_user                | 用户表              | 8       |
-- | auth_role                | 角色表              | 8       |
-- | auth_permission          | 权限表              | 8       |
-- | auth_user_role           | 用户角色关联表       | 8       |
-- | auth_role_permission     | 角色权限关联表       | 8       |
-- | auth_session             | 会话表              | 8       |
-- | auth_oauth_client        | OAuth 客户端表      | 8       |
-- | auth_social_connection   | 第三方账号绑定表     | 8       |
-- | auth_audit_log           | 操作审计日志表       | 8       |
--
-- 预留字段规范：
-- - ext_varchar_1 ~ ext_varchar_5: VARCHAR(255) 字符串扩展字段
-- - ext_int_1: INTEGER 整数扩展字段
-- - ext_json_1: JSONB JSON 扩展字段
-- - ext_timestamp_1: TIMESTAMP 时间戳扩展字段
-- ============================================================================
