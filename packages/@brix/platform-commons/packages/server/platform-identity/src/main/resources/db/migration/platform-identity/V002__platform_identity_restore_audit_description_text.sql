-- Restore the V001 TEXT type that Hibernate 7 `ddl-auto: update` narrowed to
-- VARCHAR(255) on databases started with Spring Boot 4.1.1. The entity now
-- declares TEXT. Fresh installs already have this type.

ALTER TABLE sys_platform_audit_log ALTER COLUMN description TYPE TEXT;
