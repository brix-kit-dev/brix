# Changelog — @brix-sdk/platform-admin-web

All notable changes to this package are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.2.0] — 2026-05-08

### Added — Initial release (P-8 of SSOT v1.0 §13)
* `PlatformAuthRepository` / `PlatformAdminRepository` / `PlatformAuditRepository` /
  `PlatformTenantRepository` — pure data layer, depend only on `HttpCapability`.
* React hooks: `usePlatformLogin`, `useSuperAdminList`, `useCreateSuperAdmin`,
  `useDisableSuperAdmin`, `useResetPassword`, `useChangeOwnPassword`,
  `useAuditLog`, `usePlatformTenantList`, `useUpdateTenantStatus`.
* Pages: `PlatformLoginPage`, `PlatformDashboardPage`, `SuperAdminListPage`,
  `CreateSuperAdminDialog`, `ResetPasswordDialog`, `ChangeOwnPasswordPage`,
  `AuditLogPage`, `PlatformTenantListPage`, `UpdateTenantStatusDialog`.
* Constants: `PLATFORM_ADMIN_PERMISSIONS`, `PLATFORM_ADMIN_ROUTES`,
  `PLATFORM_AUDIT_ACTIONS`, `PLATFORM_TENANT_STATUS`, `PLATFORM_ROLE_CODE`.

### Architectural Compliance
* All HTTP traffic goes through `HttpCapability` — no direct `fetch` / `axios`.
* All UI rendering uses `useUI()` + `useTheme().tokens` — no direct UI library imports.
* No `enterprise-*` imports (SSOT §11 R-1).
* All permission gating reads from `AuthCapability.hasPermission(...)` — no string-literal
  role checks (SSOT §11 R-3).
