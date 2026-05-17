/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useState } from 'react';
import { useRepositories } from './useRepositories';
import type { DisableAdminRequest, PlatformAdminDto } from '../types';

export interface UseDisableSuperAdminResult {
  loading: boolean;
  error: Error | null;
  disable: (id: string, req: DisableAdminRequest) => Promise<PlatformAdminDto>;
}

export function useDisableSuperAdmin(): UseDisableSuperAdminResult {
  const { admin } = useRepositories();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const disable = useCallback(
    async (id: string, req: DisableAdminRequest) => {
      setLoading(true);
      setError(null);
      try {
        return await admin.disable(id, req);
      } catch (e) {
        const err = e instanceof Error ? e : new Error(String(e));
        setError(err);
        throw err;
      } finally {
        setLoading(false);
      }
    },
    [admin],
  );

  return { loading, error, disable };
}
