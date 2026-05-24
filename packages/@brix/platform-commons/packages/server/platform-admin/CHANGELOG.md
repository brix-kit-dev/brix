# `@brix/platform-admin` — Changelog

All notable changes to the platform-admin server module are documented here.

## [3.2.0] — 2026-05

### Added — Platform Super-Admin v2.0

This release implements the v2.0 platform super-admin SSoT:

- Isolated platform login endpoints under `/api/platform/auth/**`.
- Setup-link based admin onboarding and password reset.
- Google Authenticator compatible TOTP setup and login flow.
- Formal `PLATFORM_SUPER_ADMIN` and passwordless `BOOTSTRAP` roles only.
- `ACTIVE` / `REVOKED` platform-admin grant lifecycle.
- `PENDING_SETUP` / `ACTIVE` / `LOCKED` / `DISABLED` identity lifecycle.
- Platform admin, tenant, bootstrap, setup, TOTP, and audit endpoints under `/api/platform/**`.
- Response-body guard for platform APIs in `test` and `prod` profiles.

### Security

- Platform-admin create and reset responses expose only identifiers and `setupLinkSent`.
- Setup tokens are stored as hashes and are delivered only through `NotificationCapability`.
- Platform JWT permission claims exclude `platform:bypass`.
- Revoke, reset, password change, TOTP binding, and bootstrap completion invalidate relevant sessions through token-version changes.
- v1 temporary-password schema artifacts are removed by forward migration `V019`.

### Architecture

- Module remains Layer 2C in `platform-commons`.
- No dependency on `enterprise-*` modules.
- Controller permission checks use permission constants, not role-name string checks.
- Shared state and infrastructure access stay behind Runtime Capability contracts.
