/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file PlatformTenantRepository — read tenants & change tenant status.
 *
 * Status transitions are validated server-side by the StatusMachine
 * (SSOT §6 endpoint #10). The client merely surfaces the legal targets
 * via {@link PLATFORM_TENANT_TRANSITIONABLE_STATUS}.
 */

import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import { PLATFORM_ADMIN_API } from '../constants';
import type {
  CreatePlatformTenantRequest,
  Page,
  PlatformTenantDto,
  TenantQuery,
  UpdateTenantStatusRequest,
} from '../types';

export interface PlatformTenantRepository {
  list(query?: TenantQuery): Promise<Page<PlatformTenantDto>>;
  create(req: CreatePlatformTenantRequest): Promise<PlatformTenantDto>;
  updateStatus(
    id: string,
    req: UpdateTenantStatusRequest,
  ): Promise<PlatformTenantDto>;
}

export function createPlatformTenantRepository(
  http: HttpCapability,
): PlatformTenantRepository {
  return {
    async list(query) {
      const response = await http.get<unknown>(
        PLATFORM_ADMIN_API.TENANTS,
        query as Record<string, unknown> | undefined,
      );
      return normalizeTenantPage(response, query);
    },
    async create(req) {
      const response = await http.post<unknown>(PLATFORM_ADMIN_API.TENANTS, req);
      return normalizeTenantDto(response as BackendPlatformTenantDto);
    },
    async updateStatus(id, req) {
      const response = await http.patch<unknown>(
        PLATFORM_ADMIN_API.TENANT_STATUS(id),
        req,
      );
      return normalizeTenantDto(response as BackendPlatformTenantDto);
    },
  };
}

interface BackendPlatformTenantDto {
  id?: string | number;
  tenantId?: string | number;
  code?: string;
  name?: string;
  status?: string;
  createdAt?: string | number | null;
  updatedAt?: string | number | null;
  ownerIdentityId?: string | null;
  memberCount?: number;
  quotaUsed?: number | null;
  usedQuota?: number | null;
  quotaLimit?: number | null;
  tenantQuota?: number | null;
  licenseStatus?: string | null;
  defaultLocale?: string | null;
  locale?: string | null;
  defaultTimezone?: string | null;
  timezone?: string | null;
  defaultTheme?: string | null;
  theme?: string | null;
}

function normalizeTenantPage(
  response: unknown,
  query?: TenantQuery,
): Page<PlatformTenantDto> {
  if (Array.isArray(response)) {
    const content = response.map((tenant) =>
      normalizeTenantDto(tenant as BackendPlatformTenantDto),
    );
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

  const page = response as Partial<Page<BackendPlatformTenantDto>>;
  const content = Array.isArray(page.content)
    ? page.content.map((tenant) => normalizeTenantDto(tenant))
    : [];
  const size = page.size ?? query?.size ?? content.length;
  const totalElements = page.totalElements ?? content.length;
  const totalPages = size > 0 ? Math.max(1, Math.ceil(totalElements / size)) : 1;
  const currentPage = page.page ?? query?.page ?? 0;
  return {
    content,
    page: currentPage,
    size,
    totalElements,
    totalPages,
    first: page.first ?? currentPage === 0,
    last: page.last ?? currentPage >= totalPages - 1,
  };
}

function normalizeTenantDto(tenant: BackendPlatformTenantDto): PlatformTenantDto {
  const id = String(tenant.id ?? tenant.tenantId ?? '');
  const createdAt = normalizeTimestamp(tenant.createdAt);
  return {
    id,
    code: tenant.code ?? '',
    name: tenant.name ?? '',
    status: tenant.status as PlatformTenantDto['status'],
    createdAt,
    updatedAt: normalizeTimestamp(tenant.updatedAt) || createdAt,
    ownerIdentityId: tenant.ownerIdentityId ?? null,
    memberCount: tenant.memberCount ?? 0,
    quotaUsed: normalizeNullableNumber(tenant.quotaUsed ?? tenant.usedQuota),
    quotaLimit: normalizeNullableNumber(tenant.quotaLimit ?? tenant.tenantQuota),
    licenseStatus: tenant.licenseStatus ?? null,
    defaultLocale: tenant.defaultLocale ?? tenant.locale ?? null,
    defaultTimezone: tenant.defaultTimezone ?? tenant.timezone ?? null,
    defaultTheme: tenant.defaultTheme ?? tenant.theme ?? null,
  };
}

function normalizeTimestamp(value: string | number | null | undefined): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'number') return new Date(value * 1000).toISOString();
  return value;
}

function normalizeNullableNumber(value: number | null | undefined): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}
