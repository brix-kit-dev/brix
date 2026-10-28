-- Expand only (super-admin §5, root BRX-RS-DATA-045 to DATA-047, platform-audit design §3.3, §3.4).
--
-- * Ids come from the owner's sequence. It is also the column default, so the runtime custodian
--   inserts without an id. platform-identity keeps assigning Snowflake ids (around 10^18) during the
--   split, far above anything the sequence reaches.
-- * New columns are nullable or defaulted and never back-filled: old rows did not record them.
-- * writer names the registered writer of each row. Rows without one are platform-identity's own
--   (exemption EX-2026-003), which is also what every existing row is.
-- * The cross-tenant policy actions are written by the runtime custodian and by nobody else, and
--   each of those rows carries an actor, a trace id and a stable reason.

CREATE SEQUENCE IF NOT EXISTS sys_platform_audit_log_id_seq;

ALTER TABLE sys_platform_audit_log ALTER COLUMN id SET DEFAULT nextval('sys_platform_audit_log_id_seq');

ALTER TABLE sys_platform_audit_log ADD COLUMN IF NOT EXISTS execution_identity VARCHAR(128);
ALTER TABLE sys_platform_audit_log ADD COLUMN IF NOT EXISTS reason_code VARCHAR(64);
ALTER TABLE sys_platform_audit_log ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
ALTER TABLE sys_platform_audit_log ADD COLUMN IF NOT EXISTS source_ip VARCHAR(64);
ALTER TABLE sys_platform_audit_log ADD COLUMN IF NOT EXISTS user_agent VARCHAR(256);
ALTER TABLE sys_platform_audit_log ADD COLUMN IF NOT EXISTS writer VARCHAR(64) NOT NULL DEFAULT 'platform-identity';

ALTER TABLE sys_platform_audit_log ADD CONSTRAINT ck_platform_audit_policy_writer
    CHECK ((action IN ('LOGIN_TENANT_DISCOVERY', 'FIRST_OWNER_INVITEE_SETUP_FANOUT'))
           = (writer = 'runtime.tenant-policy-audit'));

ALTER TABLE sys_platform_audit_log ADD CONSTRAINT ck_platform_audit_policy_fields
    CHECK (action NOT IN ('LOGIN_TENANT_DISCOVERY', 'FIRST_OWNER_INVITEE_SETUP_FANOUT')
           OR (operator_identity_id IS NOT NULL AND trace_id IS NOT NULL AND reason_code IS NOT NULL));

CREATE INDEX IF NOT EXISTS idx_platform_audit_action ON sys_platform_audit_log(action, created_at DESC);
