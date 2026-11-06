# Changelog — @brix-sdk/platform-admin-web

All notable changes to this package are documented here.

## [Unreleased]

### Changed

- Creating a tenant, and creating or resending a FIRST_OWNER invitation, sends an `Idempotency-Key`. A retry of the same
  form keeps its key, so once the backend has committed it gives the first answer again and nothing is created or sent
  twice. Resending after a resend went out takes a new key: that is another e-mail. `PlatformTenantRepository.create`,
  `createFirstOwnerInvitation` and `resendFirstOwnerInvitation` take the key as their last argument.

## [3.2.0] — 2026-05

### Added — Platform Super-Admin Web v2.0

- Repositories for platform auth, TOTP login, setup, bootstrap, admins, tenants, and audit.
- Hooks for platform login, TOTP login, setup, bootstrap, super-admin list/create/revoke/reset, own-password change, tenant lifecycle, and audit queries.
- Pages for `/platform/login`, `/platform/login/totp`, `/platform/setup`, `/platform/bootstrap`, `/platform/bootstrap/sent`, dashboard, admins, tenants, audit, and own-password change.
- Guards for platform-authenticated routes, setup-only routes, bootstrap-only routes, and tenant route separation.

### Security

- Create and reset flows never render or copy plaintext credentials.
- Bootstrap create-first-admin never reads setup tokens from API responses or builds setup URLs client-side.
- Permission gating uses `PLATFORM_ADMIN_PERMISSIONS` and has no `|| true` bypass.

### Architecture

- View → Hook → Repository layering is preserved.
- All HTTP calls go through `HttpCapability`.
- All UI rendering goes through `useUI()` and `useTheme().tokens`.
- No direct dependency on concrete UI libraries or enterprise plugins.
