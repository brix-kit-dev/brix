/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useState } from 'react';
import { useRepositories } from './useRepositories';
import type {
  PlatformTenantDto,
  UpdateTenantStatusRequest,
} from '../types';

export interface UseUpdateTenantStatusResult {
  loading: boolean;
  error: Error | null;
  update: (
    id: string,
    req: UpdateTenantStatusRequest,
  ) => Promise<PlatformTenantDto>;
}

export function useUpdateTenantStatus(): UseUpdateTenantStatusResult {
  const { tenant } = useRepositories();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const update = useCallback(
    async (id: string, req: UpdateTenantStatusRequest) => {
      setLoading(true);
      setError(null);
      try {
        return await tenant.updateStatus(id, req);
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

  return { loading, error, update };
}
