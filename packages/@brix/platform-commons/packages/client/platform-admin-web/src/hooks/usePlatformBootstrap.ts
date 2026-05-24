/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useEffect, useState } from 'react';
import { useRepositories } from './useRepositories';
import type {
  BootstrapCreateFirstAdminResponse,
  CreateFirstPlatformAdminRequest,
  PlatformBootstrapStatusResponse,
} from '../types';

export interface PlatformBootstrapCreateRequest extends CreateFirstPlatformAdminRequest {
  setupCode: string;
}

export interface UsePlatformBootstrapResult {
  status: PlatformBootstrapStatusResponse | null;
  result: BootstrapCreateFirstAdminResponse | null;
  loading: boolean;
  error: Error | null;
  refreshStatus: () => Promise<PlatformBootstrapStatusResponse>;
  createFirstAdmin: (req: PlatformBootstrapCreateRequest) => Promise<BootstrapCreateFirstAdminResponse>;
  reset: () => void;
}

export function usePlatformBootstrap(autoLoad = true): UsePlatformBootstrapResult {
  const { bootstrap } = useRepositories();
  const [status, setStatus] = useState<PlatformBootstrapStatusResponse | null>(null);
  const [result, setResult] = useState<BootstrapCreateFirstAdminResponse | null>(null);
  const [loading, setLoading] = useState(autoLoad);
  const [error, setError] = useState<Error | null>(null);

  const run = useCallback(async <T,>(operation: () => Promise<T>): Promise<T> => {
    setLoading(true);
    setError(null);
    try {
      return await operation();
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      setError(err);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const refreshStatus = useCallback(
    async () => run(async () => {
      const res = await bootstrap.status();
      setStatus(res);
      return res;
    }),
    [bootstrap, run],
  );

  const createFirstAdmin = useCallback(
    async (req: PlatformBootstrapCreateRequest) => run(async () => {
      const session = await bootstrap.session({ setupCode: req.setupCode });
      const res = await bootstrap.createFirstAdmin(
        {
          username: req.username,
          email: req.email,
          displayName: req.displayName,
        },
        session.accessToken,
      );
      setResult(res);
      return res;
    }),
    [bootstrap, run],
  );

  const reset = useCallback(() => {
    setError(null);
    setResult(null);
  }, []);

  useEffect(() => {
    if (!autoLoad) return;
    let mounted = true;
    setLoading(true);
    setError(null);
    bootstrap.status()
      .then((res) => {
        if (mounted) setStatus(res);
      })
      .catch((e) => {
        if (mounted) setError(e instanceof Error ? e : new Error(String(e)));
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, [autoLoad, bootstrap]);

  return { status, result, loading, error, refreshStatus, createFirstAdmin, reset };
}
