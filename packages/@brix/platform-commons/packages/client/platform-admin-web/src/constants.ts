/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * Centralised constants for `@brix-sdk/platform-admin-web`.
 *
 * Mirrors the backend Java constants:
 *   - `io.brix.platform.auth.api.PermissionCode`
 *   - `io.brix.platform.auth.api.RoleCode`
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
 * UI components gate on specific fine-grained codes such as `ADMIN_CREATE`.
 * Generic bypass semantics are intentionally absent from the platform UI.
 */
export const PLATFORM_ADMIN_PERMISSIONS = Object.freeze({
  /** Tenant management */
  TENANT_READ: 'platform:tenant:read',
  TENANT_CREATE: 'platform:tenant:create',
  TENANT_UPDATE_STATUS: 'platform:tenant:update-status',
  TENANT_FIRST_OWNER_INVITE: 'platform:tenant:first-owner-invite',

  /** Admin lifecycle */
  ADMIN_READ: 'platform:admin:read',
  ADMIN_CREATE: 'platform:admin:create',
  ADMIN_REVOKE: 'platform:admin:revoke',
  ADMIN_RESET_PASSWORD: 'platform:admin:reset-password',
  ADMIN_CHANGE_OWN_PASSWORD: 'platform:admin:change-own-password',

  /** Audit */
  AUDIT_READ: 'platform:audit:read',

  /** License / quota */
  LICENSE_READ: 'platform:license:read',
} as const);

export type PlatformAdminPermission =
  (typeof PLATFORM_ADMIN_PERMISSIONS)[keyof typeof PLATFORM_ADMIN_PERMISSIONS];

/* ======================================================================== *
 * Role Codes (must match `io.brix.platform.auth.RoleCode`)
 * ======================================================================== */

export const PLATFORM_ROLE_CODE = Object.freeze({
  PLATFORM_SUPER_ADMIN: 'PLATFORM_SUPER_ADMIN',
  BOOTSTRAP: 'BOOTSTRAP',
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
  SUPER_ADMIN_REVOKED: 'SUPER_ADMIN_REVOKED',
  SUPER_ADMIN_PASSWORD_RESET: 'SUPER_ADMIN_PASSWORD_RESET',
  SUPER_ADMIN_PASSWORD_CHANGED: 'SUPER_ADMIN_PASSWORD_CHANGED',
  SETUP_TOKEN_ISSUED: 'SETUP_TOKEN_ISSUED',
  SETUP_TOKEN_USED: 'SETUP_TOKEN_USED',
  SETUP_TOKEN_INVALID: 'SETUP_TOKEN_INVALID',
  IDENTITY_PASSWORD_SET: 'IDENTITY_PASSWORD_SET',
  TOTP_BOUND: 'TOTP_BOUND',
  IDENTITY_ACTIVATED: 'IDENTITY_ACTIVATED',
  IDENTITY_DISABLED: 'IDENTITY_DISABLED',
  IDENTITY_LOCKED: 'IDENTITY_LOCKED',
  BOOTSTRAP_ADMIN_CREATED: 'BOOTSTRAP_ADMIN_CREATED',
  BOOTSTRAP_ADMIN_DEACTIVATED: 'BOOTSTRAP_ADMIN_DEACTIVATED',
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
  REVOKED: 'REVOKED',
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
  LOGIN_TOTP: '/platform/login/totp',
  SETUP: '/platform/setup',
  BOOTSTRAP: '/platform/bootstrap',
  BOOTSTRAP_SENT: '/platform/bootstrap/sent',
  DASHBOARD: '/platform',
  ADMINS: '/platform/admins',
  AUDIT: '/platform/audit',
  LICENSE: '/platform/license',
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
  AUTH_LOGIN_TOTP: '/platform/auth/login/totp',
  AUTH_LOGOUT: '/platform/auth/logout',
  SETUP_VALIDATE: '/platform/auth/setup/validate',
  SETUP_TOTP_INIT: '/platform/auth/setup/totp/init',
  SETUP_COMPLETE: '/platform/auth/setup/complete',
  BOOTSTRAP_STATUS: '/platform/bootstrap/status',
  BOOTSTRAP_SESSION: '/platform/bootstrap/session',
  BOOTSTRAP_CREATE_FIRST_ADMIN: '/platform/bootstrap/create-first-admin',
  ADMINS: '/platform/admins',
  ADMIN_REVOKE: (id: string | number) => `/platform/admins/${id}/revoke`,
  ADMIN_RESET_PASSWORD: (id: string | number) =>
    `/platform/admins/${id}/reset-password`,
  ADMIN_CHANGE_OWN_PASSWORD: '/platform/admins/me/change-password',
  AUDIT_LOGS: '/platform/audit-logs',
  LICENSE_QUOTA: '/platform/license/quota',
  TENANTS: '/platform/tenants',
  TENANT_STATUS: (id: string | number) => `/platform/tenants/${id}/status`,
  TENANT_FIRST_OWNER_INVITATIONS_CURRENT: (id: string | number) =>
    `/platform/tenants/${id}/first-owner-invitations/current`,
  TENANT_FIRST_OWNER_INVITATIONS: (id: string | number) =>
    `/platform/tenants/${id}/first-owner-invitations`,
  TENANT_FIRST_OWNER_INVITATIONS_RESEND: (id: string | number) =>
    `/platform/tenants/${id}/first-owner-invitations/resend`,
  TENANT_FIRST_OWNER_INVITATION: (
    id: string | number,
    invitationId: string | number,
  ) => `/platform/tenants/${id}/first-owner-invitations/${invitationId}`,
} as const);

/* ======================================================================== *
 * EventBus topics — published from `platform-admin` backend.
 * Subscribed by the client to refresh views in real time.
 * ======================================================================== */

export const PLATFORM_ADMIN_EVENT_TOPICS = Object.freeze({
  TENANT_STATUS_CHANGED: 'platform.tenant.status_changed',
  SUPER_ADMIN_CREATED: 'platform.admin.created',
  SUPER_ADMIN_REVOKED: 'platform.admin.revoked',
} as const);
