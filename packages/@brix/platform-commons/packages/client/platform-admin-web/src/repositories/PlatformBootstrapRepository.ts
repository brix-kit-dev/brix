/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import { PLATFORM_ADMIN_API } from '../constants';
import type {
  BootstrapCreateFirstAdminResponse,
  CreateFirstPlatformAdminRequest,
  PlatformBootstrapSessionRequest,
  PlatformBootstrapSessionResponse,
  PlatformBootstrapStatusResponse,
} from '../types';

export interface PlatformBootstrapRepository {
  status(): Promise<PlatformBootstrapStatusResponse>;
  session(req: PlatformBootstrapSessionRequest): Promise<PlatformBootstrapSessionResponse>;
  createFirstAdmin(
    req: CreateFirstPlatformAdminRequest,
    bootstrapAccessToken?: string,
  ): Promise<BootstrapCreateFirstAdminResponse>;
}

export function createPlatformBootstrapRepository(
  http: HttpCapability,
): PlatformBootstrapRepository {
  return {
    async status() {
      return http.get<PlatformBootstrapStatusResponse>(
        PLATFORM_ADMIN_API.BOOTSTRAP_STATUS,
      );
    },
    async session(req) {
      return http.post<PlatformBootstrapSessionResponse>(
        PLATFORM_ADMIN_API.BOOTSTRAP_SESSION,
        req,
      );
    },
    async createFirstAdmin(req, bootstrapAccessToken) {
      if (!bootstrapAccessToken) {
        return http.post<BootstrapCreateFirstAdminResponse>(
          PLATFORM_ADMIN_API.BOOTSTRAP_CREATE_FIRST_ADMIN,
          req,
        );
      }
      const response = await http.request<BootstrapCreateFirstAdminResponse>({
        url: PLATFORM_ADMIN_API.BOOTSTRAP_CREATE_FIRST_ADMIN,
        method: 'POST',
        headers: {
          Authorization: `Bearer ${bootstrapAccessToken}`,
        },
        data: req,
      });
      return response.data;
    },
  };
}
