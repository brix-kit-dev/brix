-- P4 design §4.4 (P4e): platform-identity no longer writes sys_platform_audit_log. Its audit facts reach
-- the table through platform-audit's inbox since P4c, and its own mapping of the table is gone, which
-- closes exemption EX-2026-003. Every new row names its registered writer (platform-audit or
-- runtime.tenant-policy-audit), so the default that marked platform-identity's rows is dropped. Rows
-- written before the split keep writer = 'platform-identity'; nothing is back-filled.

ALTER TABLE sys_platform_audit_log ALTER COLUMN writer DROP DEFAULT;
