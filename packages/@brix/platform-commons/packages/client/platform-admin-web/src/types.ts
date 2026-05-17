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
 * Auth — POST /api/platform/auth/login
 * ======================================================================== */

export interface PlatformLoginRequest {
  loginId: string;
  password: string;
}

export interface PlatformLoginResponse {
  status?: 'COMPLETE' | 'PASSWORD_MUST_CHANGE' | 'MFA_REQUIRED';
  accessToken: string;
  refreshToken?: string;
  tokenType?: 'Bearer';
  expiresInSeconds?: number;
  expiresIn?: number;
  /**
   * If true, the client MUST navigate to the change-password page.
   * SSOT §8.5: until cleared, only `/admins/me/change-password` is callable.
   */
  forcePasswordChange?: boolean;
  mustChangePassword?: boolean;
  platformRole: PlatformRoleCode;
  /** Platform permission codes embedded in the JWT (already de-duplicated). */
  permissions: readonly string[];
  identityId?: string | number;
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
  forcePasswordChange: boolean;
  createdAt: string;
  createdBy: string | null;
  disabledAt: string | null;
  disabledBy: string | null;
  disableReason: string | null;
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
  admin: PlatformAdminDto;
  /**
   * Plaintext temporary password.
   *
   * SECURITY (SSOT §8.4): this value is returned EXACTLY ONCE in this response
   * and MUST be displayed to the operator immediately and discarded —
   * never persisted, never logged, never written to localStorage/sessionStorage.
   */
  tempPassword: string;
  /** ISO-8601 timestamp; the temp password expires at this instant. */
  tempPasswordExpiresAt: string;
}

export interface DisableAdminRequest {
  reason: string;
}

export interface ResetPasswordResponse {
  /** Same one-shot semantics as {@link CreatePlatformAdminResponse.tempPassword}. */
  tempPassword: string;
  tempPasswordExpiresAt: string;
}

export interface ChangeOwnPasswordRequest {
  oldPassword: string;
  newPassword: string;
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
