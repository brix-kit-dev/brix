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
  Page,
  PlatformTenantDto,
  TenantQuery,
  UpdateTenantStatusRequest,
} from '../types';

export interface PlatformTenantRepository {
  list(query?: TenantQuery): Promise<Page<PlatformTenantDto>>;
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
      return http.get<Page<PlatformTenantDto>>(
        PLATFORM_ADMIN_API.TENANTS,
        query as Record<string, unknown> | undefined,
      );
    },
    async updateStatus(id, req) {
      return http.patch<PlatformTenantDto>(
        PLATFORM_ADMIN_API.TENANT_STATUS(id),
        req,
      );
    },
  };
}
