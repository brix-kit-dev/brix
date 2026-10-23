-- identity-auth 1.0 BRX-IA-CRED-003 / RFC 6238 §5.2: a TOTP code is accepted at most
-- once. The Owner records the last accepted time step and accepts a code only for a
-- later step. NULL means no code has been accepted yet.

ALTER TABLE sys_identity ADD COLUMN IF NOT EXISTS mfa_last_used_step BIGINT;
