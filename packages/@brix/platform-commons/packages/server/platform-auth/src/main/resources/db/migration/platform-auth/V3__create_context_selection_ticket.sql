CREATE TABLE IF NOT EXISTS auth_context_selection_ticket (
    id                  BIGINT          NOT NULL PRIMARY KEY,
    ticket_hash         VARCHAR(64)     NOT NULL,
    identity_id         BIGINT          NOT NULL,
    identity_token_jti  VARCHAR(64)     NOT NULL,
    role_type           VARCHAR(16)     NOT NULL,
    tenant_id           BIGINT          NOT NULL,
    ref_id              BIGINT          NOT NULL,
    context_id          VARCHAR(36)     NOT NULL,
    issued_at           TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMPTZ     NOT NULL,
    consumed_at         TIMESTAMPTZ,

    CONSTRAINT uk_auth_context_ticket_hash UNIQUE (ticket_hash),
    CONSTRAINT ck_auth_context_ticket_role CHECK (role_type IN ('actor', 'subject'))
);

CREATE INDEX IF NOT EXISTS idx_auth_context_ticket_identity
    ON auth_context_selection_ticket(identity_id)
    WHERE consumed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_auth_context_ticket_expires
    ON auth_context_selection_ticket(expires_at)
    WHERE consumed_at IS NULL;

COMMENT ON TABLE auth_context_selection_ticket IS 'One-time context selection tickets bound to an Identity Token jti.';
COMMENT ON COLUMN auth_context_selection_ticket.ticket_hash IS 'SHA-256 Base64URL hash of the opaque client ticket.';
COMMENT ON COLUMN auth_context_selection_ticket.context_id IS 'Immutable Actor/Subject context ID used later as JWT cid.';
