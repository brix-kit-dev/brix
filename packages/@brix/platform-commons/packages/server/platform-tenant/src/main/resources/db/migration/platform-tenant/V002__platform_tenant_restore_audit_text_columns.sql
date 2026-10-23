-- Restore the V001 TEXT types that Hibernate 7 `ddl-auto: update` narrowed on
-- databases started with Spring Boot 4.1.1. The entities now declare TEXT.
-- Fresh installs already have these types.

ALTER TABLE biz_audit_log ALTER COLUMN user_agent TYPE TEXT;
ALTER TABLE biz_tenant_audit_log ALTER COLUMN description TYPE TEXT;
