/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file Internal helper that builds memoised Repository instances using the
 *       runtime-injected HttpCapability via the React `useHttp` hook.
 *
 * Every domain hook in this package routes through here so that:
 *   1. Only one HttpCapability lookup happens per render.
 *   2. Repository instances are stable references (good for `useMemo`/`useCallback`).
 *   3. Tests can mock `useHttp` once and exercise all hooks transparently.
 */

import { useMemo } from 'react';
import { useHttp } from '@brix-sdk/runtime-sdk-react';
import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import {
  createPlatformAdminRepository,
  createPlatformAuditRepository,
  createPlatformAuthRepository,
  createPlatformBootstrapRepository,
  createPlatformSetupRepository,
  createPlatformTenantRepository,
  type PlatformAdminRepository,
  type PlatformAuditRepository,
  type PlatformAuthRepository,
  type PlatformBootstrapRepository,
  type PlatformSetupRepository,
  type PlatformTenantRepository,
} from '../repositories';

export interface PlatformAdminRepositoryBundle {
  auth: PlatformAuthRepository;
  setup: PlatformSetupRepository;
  bootstrap: PlatformBootstrapRepository;
  admin: PlatformAdminRepository;
  audit: PlatformAuditRepository;
  tenant: PlatformTenantRepository;
}

export function useRepositories(): PlatformAdminRepositoryBundle {
  const http = useHttp();
  return useMemo(() => {
    // useHttp returns a structurally compatible wrapper; explicit cast
    // documents intent and avoids leaking the wrapper's surface upstream.
    const cap = http as unknown as HttpCapability;
    return {
      auth: createPlatformAuthRepository(cap),
      setup: createPlatformSetupRepository(cap),
      bootstrap: createPlatformBootstrapRepository(cap),
      admin: createPlatformAdminRepository(cap),
      audit: createPlatformAuditRepository(cap),
      tenant: createPlatformTenantRepository(cap),
    };
  }, [http]);
}
