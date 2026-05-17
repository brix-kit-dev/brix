# `@brix/platform-admin` — Changelog

All notable changes to the platform-admin server module are documented in
this file. The project adheres to [Semantic Versioning](https://semver.org/)
and the format is loosely based on [Keep a Changelog](https://keepachangelog.com/).

## [3.2.0] — 2026-01

### Added — Platform Super-Admin MVP (SSOT v1.0)

This release lands the minimum-viable platform super-admin surface defined
in [`docs/v1.0-平台超管最小实现-唯一真相来源.md`](../../../../../../docs/v1.0-平台超管最小实现-唯一真相来源.md).

#### Domain & Persistence
- New aggregate `PlatformAdmin` (id, username, email, displayName, role,
  status, passwordHash, forcePasswordChange, lastLoginAt, createdAt,
  createdBy, notes).
- New aggregate `PlatformTenant` (id, code, name, status, ownerIdentityId,
  metadata, timestamps) — read-side projection over `auth_tenant` plus
  membership counts.
- New table `platform_audit_log` — append-only forensic record with
  `actor / action / target / result / time` skeleton plus reason and IP.
- Flyway migrations:
  - `V012__platform_admin.sql` — admins, role assignments, audit log.
  - `V013__platform_admin_seed.sql` — seeds the four canonical roles
    (`SUPER_ADMIN`, `PLATFORM_ADMIN`, `SUPPORT_ADMIN`, `AUDITOR`) and the
    permission grant matrix.
  - `V014__platform_tenant_status.sql` — adds the tenant status column
    and constraint enum (`PENDING_ACTIVATION`, `ACTIVE`, `SUSPENDED`,
    `TERMINATED`).
  - `V015__platform_audit_indexes.sql` — composite indexes on
    `(actor_identity_id, created_at)` and `(action, created_at)` for the
    audit log.
  - `V016__platform_admin_constraints.sql` — case-insensitive unique
    indexes on `username` and `email`.

#### REST API (`/api/platform/**`)
- `POST /auth/login` — platform admin login (issues short-lived JWT,
  emits `SUPER_ADMIN_LOGIN_SUCCESS` / `_FAILED`).
- `POST /me/password` — self-service password change (only call allowed
  when `forcePasswordChange === true`; emits `PASSWORD_CHANGED`).
- `GET /admins` — paginated list of platform admins.
- `POST /admins` — provision a new platform admin; returns one-shot
  `tempPassword` with explicit expiry; emits `SUPER_ADMIN_CREATED`.
- `POST /admins/{id}/disable` — soft-disable an admin; requires reason;
  emits `SUPER_ADMIN_DISABLED`.
- `POST /admins/{id}/reset-password` — issue replacement temp password;
  emits `SUPER_ADMIN_PASSWORD_RESET`.
- `GET /tenants` — paginated tenant view with search/status filters.
- `PATCH /tenants/{id}/status` — transition tenant status through the
  legal `ACTIVE` ↔ `SUSPENDED` edges; emits `TENANT_STATUS_CHANGED`.
- `GET /audit` — paginated audit log query.

#### Authorisation
- New permission codes (granted by `RoleCode`):
  - `platform:tenant.read`, `platform:tenant.update_status`
  - `platform:admin.read`, `platform:admin.create`, `platform:admin.disable`,
    `platform:admin.reset_password`, `platform:admin.change_own_password`
  - `platform:audit.read`
  - `platform:bypass` — emergency break-glass (granted ONLY to bootstrap
    `SUPER_ADMIN`; never to admins created via the REST API).
- All endpoints enforce permission checks via the existing
  `AuthContextCapability`; no `@PreAuthorize("hasRole('XYZ')")` literals
  remain in code.

#### Bootstrap
- New `PlatformAdminBootstrapService` consumes
  `brix.platform.bootstrap.super-admin` YAML config and idempotently
  ensures the seed `SUPER_ADMIN` exists with `forcePasswordChange = true`.
- The bootstrap admin is the ONLY role permitted to receive
  `platform:bypass`; this is enforced by an integrity check in the
  service — production cannot start with a missing or stale bootstrap.

#### Tests
- Unit tests: 100 % method coverage on `*Service` classes.
- Integration tests: `PlatformAdminControllerIT` exercises every endpoint
  with a positive + negative path and asserts the audit log row.
- ArchUnit tests:
  - `NoEnterpriseToPlatformAdminRule` — enforces SSOT R-1/R-2.
  - `NoStringRoleLiteralRule` — enforces SSOT R-3.

### Security
- All temp-password emission paths return the cleartext exactly **once**
  via the REST response and immediately store only the bcrypt hash.
- Audit log entries are append-only — there is no UPDATE / DELETE path.
- The `SUPER_ADMIN` role is bootstrap-only; the create-admin endpoint
  refuses to accept `SUPER_ADMIN` as a target role.

### Breaking changes
None. This is a NEW module; no pre-existing API has been altered.

### Upgrade notes
1. Run Flyway migrations (V012–V016) on every environment.
2. Set `brix.platform.bootstrap.super-admin.username` /
   `…password` in the environment-specific YAML or via a sealed secret.
3. Issue an out-of-band rotation of the bootstrap password after the
   first deploy — see
   [`docs/ops/super-admin-bootstrap.md`](../../../../../../docs/ops/super-admin-bootstrap.md).

[3.2.0]: https://github.com/brix-platform/brix/releases/tag/v3.2.0
