-- Add FIRST_OWNER invitation permission to the platform permission catalog.
-- Existing environments keep V1 immutable and receive this as a forward migration.

INSERT INTO auth_permission (id, code, name, description, module, type, status, sort_order,
                             tenant_id, created_at, updated_at, deleted)
VALUES
    ('perm-platform-tenant-first-owner-invite',
     'platform:tenant:first-owner-invite',
     'Manage FIRST_OWNER invitations',
     'Create, resend, or revoke FIRST_OWNER invitations for pending activation tenants.',
     'platform-admin', 'BUTTON', 'ACTIVE', 14, 'PLATFORM',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (code, tenant_id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    module = EXCLUDED.module,
    type = EXCLUDED.type,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    deleted = FALSE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO auth_role_permission (id, role_id, permission_id, tenant_id,
                                  created_at, updated_at, deleted)
SELECT 'rp-platform-super-admin-first-owner-invite',
       'role-platform-super-admin',
       p.id,
       'PLATFORM',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       FALSE
FROM auth_permission p
WHERE p.tenant_id = 'PLATFORM'
  AND p.deleted = FALSE
  AND p.code = 'platform:tenant:first-owner-invite'
ON CONFLICT (role_id, permission_id, tenant_id) DO UPDATE SET
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;
