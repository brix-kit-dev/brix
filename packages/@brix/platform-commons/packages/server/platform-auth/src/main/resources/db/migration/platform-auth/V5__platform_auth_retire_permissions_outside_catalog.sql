-- The platform permission catalog is the ten IDs of the versioned contract io.brix.platform.auth.api.PermissionCode,
-- and Bootstrap is the type of a short-lived setup session, never a database role (super-admin §3.3, BRX-SA-AUTH-006).
-- V1 seeded ten codes outside that catalog, granted them, and seeded a BOOTSTRAP role. Nothing reads these tables, and
-- no token has carried those codes since standard-link plan queue item 18 (18e). This forward migration removes them
-- (18f, D158); V1 to V4 stay as they ran. The retired IDs are never used again.

-- A link from a user to the BOOTSTRAP role means nothing; it would keep the role row below from going.
DELETE FROM auth_user_role
WHERE role_id = 'role-platform-bootstrap';

DELETE FROM auth_role_permission
WHERE tenant_id = 'PLATFORM'
  AND (role_id = 'role-platform-bootstrap'
       OR permission_id IN (
           SELECT id FROM auth_permission
           WHERE tenant_id = 'PLATFORM'
             AND code IN ('platform:bypass',
                          'platform:tenant:manage',
                          'platform:tenant:view',
                          'platform:admin:manage',
                          'platform:audit:view',
                          'platform:system:config',
                          'platform:data:recovery',
                          'platform:license:read',
                          'platform:bootstrap:read',
                          'platform:bootstrap:create-first-admin')));

DELETE FROM auth_permission
WHERE tenant_id = 'PLATFORM'
  AND code IN ('platform:bypass',
               'platform:tenant:manage',
               'platform:tenant:view',
               'platform:admin:manage',
               'platform:audit:view',
               'platform:system:config',
               'platform:data:recovery',
               'platform:license:read',
               'platform:bootstrap:read',
               'platform:bootstrap:create-first-admin');

DELETE FROM auth_role
WHERE id = 'role-platform-bootstrap'
  AND tenant_id = 'PLATFORM';
