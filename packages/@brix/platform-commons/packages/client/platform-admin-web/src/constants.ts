/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * Centralised constants for `@brix-sdk/platform-admin-web`.
 *
 * Mirrors the backend Java constants:
 *   - `io.brix.platform.auth.PlatformPermissions`
 *   - `io.brix.platform.auth.RoleCode`
 *   - `io.brix.platform.auth.AuditAction`
 *
 * Architectural rule (SSOT §11 R-3):
 *   Pages/Hooks MUST reference these constants — never bare strings.
 *   This file is the single front-end source of truth for those values.
 *   Any drift between this file and the backend constants is a CI-blocker
 *   (covered by an integration test that diffs both sides).
 *
 * @module @brix-sdk/platform-admin-web/constants
 */

/* ======================================================================== *
 * Permissions (must match `io.brix.platform.auth.PlatformPermissions`)
 * ======================================================================== */

/**
 * Platform-scoped permission codes.
 *
 * Naming scheme: `platform:<resource>:<action>` — keep stable; codes are
 * persisted in `auth_permission` and embedded in JWTs.
 *
 * @remarks
 * `BYPASS` is intentionally NOT exposed to UI gating logic.
 * Per SSOT §4.1, `platform:bypass` MAY appear in the JWT permission list of
 * SUPER_ADMIN/PLATFORM_ADMIN, but UI components MUST gate on the specific
 * fine-grained codes (e.g. `ADMIN_CREATE`), not on `bypass`. The constant
 * is exported only so the audit/login flow can detect bypass holders for
 * defensive logging.
 */
export const PLATFORM_ADMIN_PERMISSIONS = Object.freeze({
  /** `platform:bypass` — see SSOT §4.1 usage rules. NOT for UI gating. */
  BYPASS: 'platform:bypass',

  /** Tenant management */
  TENANT_READ: 'platform:tenant:read',
  TENANT_UPDATE_STATUS: 'platform:tenant:update-status',

  /** Admin lifecycle */
  ADMIN_READ: 'platform:admin:read',
  ADMIN_CREATE: 'platform:admin:create',
  ADMIN_DISABLE: 'platform:admin:disable',
  ADMIN_RESET_PASSWORD: 'platform:admin:reset-password',
  ADMIN_CHANGE_OWN_PASSWORD: 'platform:admin:change-own-password',

  /** Audit */
  AUDIT_READ: 'platform:audit:read',
} as const);

export type PlatformAdminPermission =
  (typeof PLATFORM_ADMIN_PERMISSIONS)[keyof typeof PLATFORM_ADMIN_PERMISSIONS];

/* ======================================================================== *
 * Role Codes (must match `io.brix.platform.auth.RoleCode`)
 * ======================================================================== */

export const PLATFORM_ROLE_CODE = Object.freeze({
  SUPER_ADMIN: 'SUPER_ADMIN',
  PLATFORM_ADMIN: 'PLATFORM_ADMIN',
  SUPPORT_ADMIN: 'SUPPORT_ADMIN',
  AUDITOR: 'AUDITOR',
} as const);

export type PlatformRoleCode =
  (typeof PLATFORM_ROLE_CODE)[keyof typeof PLATFORM_ROLE_CODE];

/* ======================================================================== *
 * Audit Action Codes (must match `io.brix.platform.auth.AuditAction`)
 * ======================================================================== */

export const PLATFORM_AUDIT_ACTIONS = Object.freeze({
  SUPER_ADMIN_LOGIN_SUCCESS: 'SUPER_ADMIN_LOGIN_SUCCESS',
  SUPER_ADMIN_LOGIN_FAILED: 'SUPER_ADMIN_LOGIN_FAILED',
  SUPER_ADMIN_LOGOUT: 'SUPER_ADMIN_LOGOUT',
  SUPER_ADMIN_CREATED: 'SUPER_ADMIN_CREATED',
  SUPER_ADMIN_DISABLED: 'SUPER_ADMIN_DISABLED',
  SUPER_ADMIN_PASSWORD_RESET: 'SUPER_ADMIN_PASSWORD_RESET',
  SUPER_ADMIN_PASSWORD_CHANGED: 'SUPER_ADMIN_PASSWORD_CHANGED',
  TENANT_STATUS_CHANGED: 'TENANT_STATUS_CHANGED',
} as const);

export type PlatformAuditAction =
  (typeof PLATFORM_AUDIT_ACTIONS)[keyof typeof PLATFORM_AUDIT_ACTIONS];

/* ======================================================================== *
 * Tenant Status (must match `io.brix.platform.tenant.enums.TenantStatus`)
 * ======================================================================== */

export const PLATFORM_TENANT_STATUS = Object.freeze({
  PENDING_ACTIVATION: 'PENDING_ACTIVATION',
  ACTIVE: 'ACTIVE',
  SUSPENDED: 'SUSPENDED',
  TERMINATED: 'TERMINATED',
} as const);

export type PlatformTenantStatus =
  (typeof PLATFORM_TENANT_STATUS)[keyof typeof PLATFORM_TENANT_STATUS];

/**
 * Statuses that an operator may legally transition TO via the Platform API.
 * SSOT §6 endpoint #10: PATCH `/api/platform/tenants/{id}/status`.
 *
 * `PENDING_ACTIVATION` is excluded — it is set only at provisioning time.
 * `TERMINATED` is excluded from the MVP (irreversible — out of scope).
 */
export const PLATFORM_TENANT_TRANSITIONABLE_STATUS: readonly PlatformTenantStatus[] =
  Object.freeze([PLATFORM_TENANT_STATUS.ACTIVE, PLATFORM_TENANT_STATUS.SUSPENDED]);

/* ======================================================================== *
 * Admin Status
 * ======================================================================== */

export const PLATFORM_ADMIN_STATUS = Object.freeze({
  ACTIVE: 'ACTIVE',
  DISABLED: 'DISABLED',
} as const);

export type PlatformAdminStatus =
  (typeof PLATFORM_ADMIN_STATUS)[keyof typeof PLATFORM_ADMIN_STATUS];

/* ======================================================================== *
 * Routes — Single source of truth for client-side router paths.
 *
 * Convention: prefix `/platform` matches the backend gateway prefix
 * `/api/platform/**`. Pages are co-located here so the consuming host's
 * router does not have to invent paths.
 * ======================================================================== */

export const PLATFORM_ADMIN_ROUTES = Object.freeze({
  LOGIN: '/platform/login',
  DASHBOARD: '/platform',
  ADMINS: '/platform/admins',
  AUDIT: '/platform/audit',
  TENANTS: '/platform/tenants',
  CHANGE_OWN_PASSWORD: '/platform/me/password',
} as const);

export type PlatformAdminRoute =
  (typeof PLATFORM_ADMIN_ROUTES)[keyof typeof PLATFORM_ADMIN_ROUTES];

/* ======================================================================== *
 * Backend API endpoints — kept here so Repositories never invent paths.
 * Mirrors SSOT §6 endpoint table.
 * ======================================================================== */

export const PLATFORM_ADMIN_API = Object.freeze({
  AUTH_LOGIN: '/platform/auth/login',
  AUTH_LOGOUT: '/platform/auth/logout',
  ADMINS: '/platform/admins',
  ADMIN_DISABLE: (id: string | number) => `/platform/admins/${id}/disable`,
  ADMIN_RESET_PASSWORD: (id: string | number) =>
    `/platform/admins/${id}/reset-password`,
  ADMIN_CHANGE_OWN_PASSWORD: '/platform/admins/me/change-password',
  AUDIT_LOGS: '/platform/audit-logs',
  TENANTS: '/platform/tenants',
  TENANT_STATUS: (id: string | number) => `/platform/tenants/${id}/status`,
} as const);

/* ======================================================================== *
 * EventBus topics — published from `platform-admin` backend.
 * Subscribed by the client to refresh views in real time.
 * ======================================================================== */

export const PLATFORM_ADMIN_EVENT_TOPICS = Object.freeze({
  TENANT_STATUS_CHANGED: 'platform.tenant.status_changed',
  SUPER_ADMIN_CREATED: 'platform.admin.created',
  SUPER_ADMIN_DISABLED: 'platform.admin.disabled',
} as const);
