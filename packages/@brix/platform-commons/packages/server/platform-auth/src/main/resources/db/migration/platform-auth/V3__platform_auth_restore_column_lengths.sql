-- Restore the V1 column lengths that Hibernate 7 `ddl-auto: update` changed on
-- databases started with Spring Boot 4.1.1. The mapping entities now declare the
-- V1 lengths, so the columns keep them. Fresh installs already have these types.

ALTER TABLE auth_permission ALTER COLUMN code TYPE VARCHAR(150);
ALTER TABLE auth_role_permission ALTER COLUMN id TYPE VARCHAR(128);
ALTER TABLE auth_user ALTER COLUMN avatar_url TYPE VARCHAR(500);
