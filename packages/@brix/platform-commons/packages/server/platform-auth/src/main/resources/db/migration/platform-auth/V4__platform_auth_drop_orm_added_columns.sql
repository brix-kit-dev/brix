-- Drop the columns that an ORM `ddl-auto: update` added to these tables outside
-- the platform-auth migrations, for entity mappings that no longer exist. V1 never
-- defined them and nothing reads them; fresh installs do not have them.

ALTER TABLE auth_user
    DROP COLUMN IF EXISTS display_name,
    DROP COLUMN IF EXISTS email_verified,
    DROP COLUMN IF EXISTS failed_login_count,
    DROP COLUMN IF EXISTS last_login_ip,
    DROP COLUMN IF EXISTS locked_until,
    DROP COLUMN IF EXISTS password_changed_at,
    DROP COLUMN IF EXISTS phone_verified,
    DROP COLUMN IF EXISTS role;

ALTER TABLE auth_role_permission DROP COLUMN IF EXISTS data_scope;

ALTER TABLE auth_user_role
    DROP COLUMN IF EXISTS valid_from,
    DROP COLUMN IF EXISTS valid_until;
