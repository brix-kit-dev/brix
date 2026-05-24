/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import { PLATFORM_ADMIN_API } from '../constants';
import type {
  PlatformSetupCompleteRequest,
  PlatformSetupCompleteResponse,
  PlatformSetupTotpInitResponse,
  PlatformSetupValidateResponse,
} from '../types';

export interface PlatformSetupRepository {
  validate(token: string): Promise<PlatformSetupValidateResponse>;
  initTotp(token: string): Promise<PlatformSetupTotpInitResponse>;
  complete(req: PlatformSetupCompleteRequest): Promise<PlatformSetupCompleteResponse>;
}

export function createPlatformSetupRepository(
  http: HttpCapability,
): PlatformSetupRepository {
  return {
    async validate(token) {
      return http.get<PlatformSetupValidateResponse>(
        PLATFORM_ADMIN_API.SETUP_VALIDATE,
        { token },
      );
    },
    async initTotp(token) {
      return http.post<PlatformSetupTotpInitResponse>(
        PLATFORM_ADMIN_API.SETUP_TOTP_INIT,
        { token },
      );
    },
    async complete(req) {
      return http.post<PlatformSetupCompleteResponse>(
        PLATFORM_ADMIN_API.SETUP_COMPLETE,
        req,
      );
    },
  };
}
