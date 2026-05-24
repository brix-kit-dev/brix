/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useState } from 'react';
import { useRepositories } from './useRepositories';
import type {
  CreatePlatformTenantRequest,
  PlatformTenantDto,
} from '../types';

export interface UseCreatePlatformTenantResult {
  loading: boolean;
  error: Error | null;
  create: (req: CreatePlatformTenantRequest) => Promise<PlatformTenantDto>;
}

export function useCreatePlatformTenant(): UseCreatePlatformTenantResult {
  const { tenant } = useRepositories();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const create = useCallback(
    async (req: CreatePlatformTenantRequest) => {
      setLoading(true);
      setError(null);
      try {
        return await tenant.create(req);
      } catch (e) {
        const err = e instanceof Error ? e : new Error(String(e));
        setError(err);
        throw err;
      } finally {
        setLoading(false);
      }
    },
    [tenant],
  );

  return { loading, error, create };
}
