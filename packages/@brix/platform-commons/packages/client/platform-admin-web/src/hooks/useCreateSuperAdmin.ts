/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useState } from 'react';
import { useRepositories } from './useRepositories';
import type {
  CreatePlatformAdminRequest,
  CreatePlatformAdminResponse,
} from '../types';

export interface UseCreateSuperAdminResult {
  loading: boolean;
  error: Error | null;
  /** One-shot temp-password response. Cleared by `acknowledge()`. */
  result: CreatePlatformAdminResponse | null;
  create: (req: CreatePlatformAdminRequest) => Promise<CreatePlatformAdminResponse>;
  /**
   * Caller MUST call this after the operator has copied the temp password.
   * Drops the password from React state so it never lingers in the heap
   * longer than necessary (SSOT §8.4).
   */
  acknowledge: () => void;
}

export function useCreateSuperAdmin(): UseCreateSuperAdminResult {
  const { admin } = useRepositories();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [result, setResult] = useState<CreatePlatformAdminResponse | null>(null);

  const create = useCallback(
    async (req: CreatePlatformAdminRequest) => {
      setLoading(true);
      setError(null);
      try {
        const res = await admin.create(req);
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

  return { loading, error, result, create, acknowledge };
}
