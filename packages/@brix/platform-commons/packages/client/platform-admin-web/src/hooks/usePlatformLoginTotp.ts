/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useState } from 'react';
import { useRepositories } from './useRepositories';
import type {
  PlatformLoginTotpRequest,
  PlatformLoginTotpResponse,
} from '../types';

export interface UsePlatformLoginTotpResult {
  loading: boolean;
  error: Error | null;
  result: PlatformLoginTotpResponse | null;
  loginTotp: (req: PlatformLoginTotpRequest) => Promise<PlatformLoginTotpResponse>;
  reset: () => void;
}

export function usePlatformLoginTotp(): UsePlatformLoginTotpResult {
  const { auth } = useRepositories();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [result, setResult] = useState<PlatformLoginTotpResponse | null>(null);

  const loginTotp = useCallback(
    async (req: PlatformLoginTotpRequest) => {
      setLoading(true);
      setError(null);
      try {
        const res = await auth.loginTotp(req);
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

  return { loading, error, result, loginTotp, reset };
}