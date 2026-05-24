/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file usePlatformLogin — drives the Platform Super-Admin login form.
 *
 * Three-state contract (SSOT §9 acceptance #2):
 *   `idle` | `loading` | `error`. On success the hook does NOT navigate —
 *   the caller routes to the TOTP page with the short-lived MFA challenge in
 *   router state.
 */

import { useCallback, useState } from 'react';
import { useRepositories } from './useRepositories';
import type {
  PlatformLoginRequest,
  PlatformLoginResponse,
} from '../types';

export interface UsePlatformLoginResult {
  loading: boolean;
  error: Error | null;
  result: PlatformLoginResponse | null;
  login: (req: PlatformLoginRequest) => Promise<PlatformLoginResponse>;
  reset: () => void;
}

export function usePlatformLogin(): UsePlatformLoginResult {
  const { auth } = useRepositories();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [result, setResult] = useState<PlatformLoginResponse | null>(null);

  const login = useCallback(
    async (req: PlatformLoginRequest) => {
      setLoading(true);
      setError(null);
      try {
        const res = await auth.login(req);
        setResult(res);
        return res;
      } catch (e) {
        const err = e instanceof Error ? e : new Error(String(e));
        setError(err);
        throw err;
      } finally {
        setLoading(false);
      }
    },
    [auth],
  );

  const reset = useCallback(() => {
    setError(null);
    setResult(null);
  }, []);

  return { loading, error, result, login, reset };
}
