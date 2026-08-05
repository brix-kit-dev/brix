-- Pre-release clean initialization for the platform-identity Data Owner.
-- Reset boundary: development databases and Flyway history may be discarded.

CREATE TABLE IF NOT EXISTS sys_identity (
    id                       BIGINT       PRIMARY KEY,
    username                 VARCHAR(128) NOT NULL,
    email                    VARCHAR(256) NOT NULL,
    password_hash            VARCHAR(256),
    status                   VARCHAR(32)  NOT NULL DEFAULT 'PENDING_SETUP',
    email_verified           BOOLEAN      NOT NULL DEFAULT FALSE,
    password_must_change     BOOLEAN      NOT NULL DEFAULT FALSE,
    token_version            BIGINT       NOT NULL DEFAULT 1,
    last_login_at            TIMESTAMPTZ,
    failed_login_count       INTEGER      NOT NULL DEFAULT 0,
    locked_until             TIMESTAMPTZ,
    last_login_ip            VARCHAR(64),
    mfa_secret_encrypted     VARCHAR(512),
    mfa_enabled              BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_bound_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ,
    CONSTRAINT uk_sys_identity_email UNIQUE (email),
    CONSTRAINT chk_sys_identity_status CHECK (status IN ('PENDING_SETUP', 'ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT chk_sys_identity_failed_login_non_negative CHECK (failed_login_count >= 0),
    CONSTRAINT chk_sys_identity_token_version_positive CHECK (token_version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_sys_identity_status ON sys_identity(status);
CREATE INDEX IF NOT EXISTS idx_sys_identity_email_verified ON sys_identity(email_verified);
CREATE INDEX IF NOT EXISTS idx_sys_identity_last_login ON sys_identity(last_login_at DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_sys_identity_locked_until ON sys_identity(locked_until);

CREATE TABLE IF NOT EXISTS sys_platform_admin (
    id                       BIGINT       PRIMARY KEY,
    identity_id              BIGINT       NOT NULL,
    role                     VARCHAR(32)  NOT NULL,
    status                   VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    mfa_enabled              BOOLEAN      NOT NULL DEFAULT FALSE,
    notes                    TEXT,
    created_by               BIGINT,
    revoked_at               TIMESTAMPTZ,
    revoked_by               BIGINT,
    revoke_reason            VARCHAR(512),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ,
    CONSTRAINT fk_platform_admin_identity FOREIGN KEY (identity_id) REFERENCES sys_identity(id) ON DELETE CASCADE,
    CONSTRAINT uk_sys_platform_admin_identity UNIQUE (identity_id),
    CONSTRAINT chk_sys_platform_admin_role CHECK (role IN ('PLATFORM_SUPER_ADMIN', 'BOOTSTRAP')),
    CONSTRAINT chk_sys_platform_admin_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE INDEX IF NOT EXISTS idx_sys_platform_admin_role ON sys_platform_admin(role);
CREATE INDEX IF NOT EXISTS idx_sys_platform_admin_status ON sys_platform_admin(status);
CREATE INDEX IF NOT EXISTS idx_sys_platform_admin_mfa ON sys_platform_admin(mfa_enabled) WHERE mfa_enabled = FALSE;
CREATE INDEX IF NOT EXISTS idx_sys_platform_admin_created_by ON sys_platform_admin(created_by);

CREATE TABLE IF NOT EXISTS sys_setup_token (
    id                       BIGINT       PRIMARY KEY,
    identity_id              BIGINT       NOT NULL,
    purpose                  VARCHAR(64)  NOT NULL,
    token_hash               VARCHAR(128) NOT NULL,
    expires_at               TIMESTAMPTZ  NOT NULL,
    used_at                  TIMESTAMPTZ,
    created_by               BIGINT,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ,
    CONSTRAINT fk_setup_token_identity FOREIGN KEY (identity_id) REFERENCES sys_identity(id) ON DELETE CASCADE,
    CONSTRAINT uk_sys_setup_token_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_sys_setup_token_identity ON sys_setup_token(identity_id);
CREATE INDEX IF NOT EXISTS idx_sys_setup_token_hash ON sys_setup_token(token_hash);

CREATE TABLE IF NOT EXISTS sys_bootstrap_state (
    id                          BIGINT      PRIMARY KEY,
    bootstrap_identity_id        BIGINT,
    setup_code_hash              VARCHAR(128),
    setup_code_expires_at        TIMESTAMPTZ,
    setup_code_used_at           TIMESTAMPTZ,
    bootstrap_session_jti        VARCHAR(128),
    bootstrap_session_expires_at TIMESTAMPTZ,
    bootstrap_session_used_at    TIMESTAMPTZ,
    completed_at                 TIMESTAMPTZ,
    completed_by_identity_id     BIGINT,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMPTZ
);

INSERT INTO sys_bootstrap_state (id, created_at, updated_at)
VALUES (1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
    updated_at = sys_bootstrap_state.updated_at;

CREATE TABLE IF NOT EXISTS sys_platform_audit_log (
    id                       BIGINT       PRIMARY KEY,
    operator_identity_id     BIGINT,
    action                   VARCHAR(64)  NOT NULL,
    resource_type            VARCHAR(64)  NOT NULL,
    resource_id              VARCHAR(128),
    affected_tenants         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    description              TEXT,
    context                  JSONB,
    success                  BOOLEAN      NOT NULL DEFAULT TRUE,
    error_code               VARCHAR(64),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_platform_audit_time ON sys_platform_audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_platform_audit_operator ON sys_platform_audit_log(operator_identity_id);
