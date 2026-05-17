/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file PlatformAuthRepository — login / logout for the platform-admin backend.
 *
 * Architectural compliance (SSOT §11):
 *   - R-3 / R-6: Pure data layer. Accepts an injected {@link HttpCapability};
 *     never imports `fetch` / `axios` / `window.fetch` directly.
 *   - Token persistence is intentionally OUT OF SCOPE here — the host shell
 *     owns token lifecycle (it injects the token into HttpCapability headers
 *     via its interceptor and persists it via AuthCapability).
 */

import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import { PLATFORM_ADMIN_API } from '../constants';
import type {
  PlatformLoginRequest,
  PlatformLoginResponse,
} from '../types';

export interface PlatformAuthRepository {
  login(req: PlatformLoginRequest): Promise<PlatformLoginResponse>;
  logout(): Promise<void>;
}

/**
 * Factory — accepts the HttpCapability the host shell wires into the runtime.
 *
 * Why a factory and not a class with a constructor?
 *   * The runtime container resolves capabilities lazily; consumers receive
 *     the instance via React context. A pure factory keeps the surface
 *     framework-agnostic and trivially mockable in tests.
 */
export function createPlatformAuthRepository(
  http: HttpCapability,
): PlatformAuthRepository {
  return {
    async login(req) {
      // POST /api/platform/auth/login — see SSOT §6 endpoint #1.
      // Returns: token + forcePasswordChange flag + permission set.
      return http.post<PlatformLoginResponse>(
        PLATFORM_ADMIN_API.AUTH_LOGIN,
        req,
      );
    },
    async logout() {
      // Endpoint is fire-and-forget; the host shell still clears the token
      // locally regardless of the network outcome.
      await http.post<void>(PLATFORM_ADMIN_API.AUTH_LOGOUT, {});
    },
  };
}
