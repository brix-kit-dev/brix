/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * DTO type definitions for `@brix-sdk/platform-admin-web`.
 *
 * Mirrors the backend DTOs under
 * `io.brix.platform.admin.dto` (server module `platform-admin`).
 *
 * Keep field names in 1-to-1 alignment with the Jackson-serialised JSON
 * produced by the Spring controllers; a backend rename without a sync
 * here is a contract break (covered by E2E test §14 acceptance #6).
 *
 * @module @brix-sdk/platform-admin-web/types
 */

import type {
  PlatformAdminStatus,
  PlatformRoleCode,
  PlatformTenantStatus,
} from './constants';

/* ======================================================================== *
 * Pagination — common envelope used by list endpoints.
 * ======================================================================== */

export interface PageRequest {
  /** Zero-based page index. */
  page?: number;
  /** Page size (server caps at 200). */
  size?: number;
  /** Sort spec, e.g. `"createdAt,desc"`. */
  sort?: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/* ======================================================================== *
 * Auth — POST /api/platform/auth/login + /login/totp
 * ======================================================================== */

export interface PlatformLoginRequest {
  loginId: string;
  password: string;
}

export interface PlatformLoginResponse {
  mfaRequired: true;
  mfaChallengeToken: string;
  expiresInSeconds?: number;
}

export interface PlatformLoginTotpRequest {
  mfaChallengeToken: string;
  totpCode: string;
}

export interface PlatformLoginTotpResponse {
  accessToken: string;
  refreshToken?: string;
  tokenType?: 'Bearer';
  expiresInSeconds?: number;
  expiresIn?: number;
  platformRole: PlatformRoleCode;
  /** Platform permission codes embedded in the JWT (already de-duplicated). */
  permissions: readonly string[];
  identityId?: string;
  username?: string;
  email?: string;
  displayName?: string | null;
}

/* ======================================================================== *
 * Admin — Platform-admin lifecycle DTOs
 * ======================================================================== */

export interface PlatformAdminDto {
  id: string;
  identityId: string;
  username: string;
  email: string | null;
  displayName: string | null;
  role: PlatformRoleCode;
  status: PlatformAdminStatus;
  identityStatus?: 'PENDING_SETUP' | 'ACTIVE' | 'LOCKED' | 'REVOKED';
  createdAt: string;
  createdBy: string | null;
  revokedAt: string | null;
  revokedBy: string | null;
  revokeReason: string | null;
  lastLoginAt: string | null;
  lastLoginIp: string | null;
}

export interface CreatePlatformAdminRequest {
  username: string;
  email: string;
  displayName?: string;
  role: PlatformRoleCode;
  notes?: string;
}

export interface CreatePlatformAdminResponse {
  id: string;
  identityId: string;
  setupLinkSent: boolean;
}

export interface RevokeAdminRequest {
  reason: string;
}

export interface ResetPasswordResponse {
  setupLinkSent: boolean;
}

export interface ChangeOwnPasswordRequest {
  oldPassword: string;
  newPassword: string;
  totpCode: string;
}

/* ======================================================================== *
 * Setup — /api/platform/auth/setup/**
 * ======================================================================== */

export interface PlatformSetupValidateResponse {
  valid: boolean;
  identityId?: string;
  loginId?: string;
  username?: string;
  email?: string | null;
  displayName?: string | null;
  purpose?: 'INITIAL_SETUP' | 'PASSWORD_RESET' | string;
  expiresAt?: string;
}

export interface PlatformSetupTotpInitResponse {
  challengeId: string;
  otpauthUri: string;
  qrCodeDataUri?: string;
  issuer?: string;
  accountName?: string;
}

export interface PlatformSetupCompleteRequest {
  token: string;
  challengeId: string;
  password: string;
  totpCode: string;
}

export interface PlatformSetupCompleteResponse {
  activated: boolean;
}

/* ======================================================================== *
 * Bootstrap — /api/platform/bootstrap/**
 * ======================================================================== */

export interface PlatformBootstrapStatusResponse {
  open: boolean;
  setupCodeExpiresAt?: string | null;
  completedAt?: string | null;
  message?: string;
}

export interface PlatformBootstrapSessionRequest {
  setupCode: string;
}

export interface PlatformBootstrapSessionResponse {
  tokenType?: string;
  accessToken: string;
  expiresIn?: number;
}

export interface CreateFirstPlatformAdminRequest {
  username: string;
  email: string;
  displayName?: string;
}

export interface BootstrapCreateFirstAdminResponse {
  id?: string;
  identityId?: string;
  setupLinkSent: boolean;
}

/* ======================================================================== *
 * Audit — GET /api/platform/audit-logs
 * ======================================================================== */

export interface PlatformAuditLogDto {
  id: string;
  actorIdentityId: string | null;
  actorUsername: string | null;
  action: string;
  targetType: string;
  targetId: string | null;
  tenantId: string | null;
  ip: string | null;
  userAgent: string | null;
  result: 'SUCCESS' | 'FAILURE';
  reason: string | null;
  requestId: string | null;
  createdAt: string;
}

export interface AuditLogQuery extends PageRequest {
  actorIdentityId?: string;
  action?: string;
  result?: 'SUCCESS' | 'FAILURE';
  tenantId?: string;
  /** ISO-8601 lower bound (inclusive). */
  fromTime?: string;
  /** ISO-8601 upper bound (exclusive). */
  toTime?: string;
}

/* ======================================================================== *
 * License / Quota — GET /api/platform/license/quota
 * ======================================================================== */

export interface InstallationQuotaDto {
  installationId: string;
  quota: number;
  used: number;
  licenseStatus: string;
  expiresAt: string | null;
  canCreateTenant: boolean;
  refusalReason: string | null;
  updatedAt: string | null;
}

/* ======================================================================== *
 * Tenant — GET /api/platform/tenants & PATCH /status
 * ======================================================================== */

export interface PlatformTenantDto {
  id: string;
  code: string;
  name: string;
  status: PlatformTenantStatus;
  createdAt: string;
  updatedAt: string;
  ownerIdentityId: string | null;
  memberCount: number;
  /** Installation or tenant quota currently used, when exposed by backend. */
  quotaUsed: number | null;
  /** Installation or tenant quota limit, when exposed by backend. */
  quotaLimit: number | null;
  /** License state summary for tenant creation/operation, when exposed. */
  licenseStatus: string | null;
  /** Effective tenant default locale, when exposed. */
  defaultLocale: string | null;
  /** Effective tenant default timezone, when exposed. */
  defaultTimezone: string | null;
  /** Effective tenant default theme, when exposed. */
  defaultTheme: string | null;
}

export interface TenantQuery extends PageRequest {
  status?: PlatformTenantStatus;
  /** Free-text search across `code` and `name`. */
  q?: string;
}

export interface UpdateTenantStatusRequest {
  status: PlatformTenantStatus;
  reason: string;
}

/**
 * Request body for POST /api/platform/tenants.
 *
 * - `code` must match `^[a-z][a-z0-9]*(-[a-z0-9]+)*$` (2–64 chars).
 * - `name` is the human-readable display name (1–256 chars).
 *
 * Tenant is created in PENDING_ACTIVATION status; use
 * UpdateTenantStatusRequest to activate it.
 */
export interface CreatePlatformTenantRequest {
  code: string;
  name: string;
}
