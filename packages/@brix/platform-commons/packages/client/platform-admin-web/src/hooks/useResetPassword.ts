/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useState } from 'react';
import { useRepositories } from './useRepositories';
import type { ResetPasswordResponse } from '../types';

export interface UseResetPasswordResult {
  loading: boolean;
  error: Error | null;
  result: ResetPasswordResponse | null;
  reset: (id: string) => Promise<ResetPasswordResponse>;
  acknowledge: () => void;
}

/**
 * Requests a setup-link reset for an existing platform admin.
 */
export function useResetPassword(): UseResetPasswordResult {
  const { admin } = useRepositories();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [result, setResult] = useState<ResetPasswordResponse | null>(null);

  const reset = useCallback(
    async (id: string) => {
      setLoading(true);
      setError(null);
      try {
        const res = await admin.resetPassword(id);
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
    [admin],
  );

  const acknowledge = useCallback(() => setResult(null), []);
  return { loading, error, result, reset, acknowledge };
}
