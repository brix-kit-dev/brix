/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useState } from 'react';
import { useRepositories } from './useRepositories';
import type { ChangeOwnPasswordRequest } from '../types';

export interface UseChangeOwnPasswordResult {
  loading: boolean;
  error: Error | null;
  success: boolean;
  change: (req: ChangeOwnPasswordRequest) => Promise<void>;
  reset: () => void;
}

/**
 * Self-service password change. The only endpoint callable while
 * `forcePasswordChange=true` (SSOT §8.5).
 */
export function useChangeOwnPassword(): UseChangeOwnPasswordResult {
  const { admin } = useRepositories();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [success, setSuccess] = useState(false);

  const change = useCallback(
    async (req: ChangeOwnPasswordRequest) => {
      setLoading(true);
      setError(null);
      setSuccess(false);
      try {
        await admin.changeOwnPassword(req);
        setSuccess(true);
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

  const reset = useCallback(() => {
    setError(null);
    setSuccess(false);
  }, []);

  return { loading, error, success, change, reset };
}
