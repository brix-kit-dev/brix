/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useState } from 'react';
import { useRepositories } from './useRepositories';
import type { RevokeAdminRequest } from '../types';

export interface UseRevokeSuperAdminResult {
  loading: boolean;
  error: Error | null;
  revoke: (id: string, req: RevokeAdminRequest) => Promise<void>;
}

export function useRevokeSuperAdmin(): UseRevokeSuperAdminResult {
  const { admin } = useRepositories();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const revoke = useCallback(
    async (id: string, req: RevokeAdminRequest) => {
      setLoading(true);
      setError(null);
      try {
        await admin.revoke(id, req);
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

  return { loading, error, revoke };
}