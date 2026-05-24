/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file PlatformAdminRepository — CRUD over platform super-admin accounts.
 *
 * Mirrors backend `io.brix.platform.admin.controller.PlatformAdminController`
 * (server module `platform-admin`). All endpoints listed in SSOT §6.
 */

import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import { PLATFORM_ADMIN_API } from '../constants';
import type {
  ChangeOwnPasswordRequest,
  CreatePlatformAdminRequest,
  CreatePlatformAdminResponse,
  Page,
  PageRequest,
  PlatformAdminDto,
  RevokeAdminRequest,
  ResetPasswordResponse,
} from '../types';

export interface PlatformAdminRepository {
  list(query?: PageRequest): Promise<Page<PlatformAdminDto>>;
  create(
    req: CreatePlatformAdminRequest,
  ): Promise<CreatePlatformAdminResponse>;
  revoke(id: string, req: RevokeAdminRequest): Promise<void>;
  resetPassword(id: string): Promise<ResetPasswordResponse>;
  changeOwnPassword(req: ChangeOwnPasswordRequest): Promise<void>;
}

export function createPlatformAdminRepository(
  http: HttpCapability,
): PlatformAdminRepository {
  return {
    async list(query) {
      // Admin-management data must always be authoritative: bypass the HTTP-layer
      // GET cache so explicit Refresh and post-mutation reloads see server state.
      const queryObj = (query as Record<string, unknown> | undefined) ?? {};
      const response = await http.get<unknown>(
        PLATFORM_ADMIN_API.ADMINS,
        { ...queryObj, headers: { 'Cache-Control': 'no-cache' } },
      );
      return normalizeAdminPage(response, query);
    },
    async create(req) {
      return http.post<CreatePlatformAdminResponse>(
        PLATFORM_ADMIN_API.ADMINS,
        req,
      );
    },
    async revoke(id, req) {
      await http.patch<void>(
        PLATFORM_ADMIN_API.ADMIN_REVOKE(id),
        req,
      );
    },
    async resetPassword(id) {
      return http.post<ResetPasswordResponse>(
        PLATFORM_ADMIN_API.ADMIN_RESET_PASSWORD(id),
        {},
      );
    },
    async changeOwnPassword(req) {
      await http.post<void>(
        PLATFORM_ADMIN_API.ADMIN_CHANGE_OWN_PASSWORD,
        req,
      );
    },
  };
}

interface BackendPlatformAdminDto extends Omit<Partial<PlatformAdminDto>, 'createdAt'> {
  adminId?: string | number;
  createdAt?: string | number | null;
}

function normalizeAdminPage(
  response: unknown,
  query?: PageRequest,
): Page<PlatformAdminDto> {
  if (Array.isArray(response)) {
    const content = response.map(normalizeAdminDto);
    return {
      content,
      page: query?.page ?? 0,
      size: query?.size ?? content.length,
      totalElements: content.length,
      totalPages: 1,
      first: true,
      last: true,
    };
  }

  const page = response as Partial<Page<BackendPlatformAdminDto>>;
  const content = Array.isArray(page.content)
    ? page.content.map(normalizeAdminDto)
    : [];
  return {
    content,
    page: page.page ?? query?.page ?? 0,
    size: page.size ?? query?.size ?? content.length,
    totalElements: page.totalElements ?? content.length,
    totalPages: page.totalPages ?? 1,
    first: page.first ?? true,
    last: page.last ?? true,
  };
}

function normalizeAdminDto(admin: BackendPlatformAdminDto): PlatformAdminDto {
  const id = String(admin.id ?? admin.adminId ?? admin.identityId ?? '');
  return {
    id,
    identityId: String(admin.identityId ?? id),
    username: admin.username ?? '',
    email: admin.email ?? null,
    displayName: admin.displayName ?? null,
    role: admin.role as PlatformAdminDto['role'],
    status: admin.status as PlatformAdminDto['status'],
    identityStatus: admin.identityStatus,
    createdAt: normalizeTimestamp(admin.createdAt),
    createdBy: admin.createdBy ?? null,
    revokedAt: admin.revokedAt ?? null,
    revokedBy: admin.revokedBy ?? null,
    revokeReason: admin.revokeReason ?? null,
    lastLoginAt: admin.lastLoginAt ?? null,
    lastLoginIp: admin.lastLoginIp ?? null,
  };
}

function normalizeTimestamp(value: string | number | null | undefined): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'number') return new Date(value * 1000).toISOString();
  return value;
}
