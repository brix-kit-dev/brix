/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useRef, useState } from 'react';
import { createIdempotencyKeys } from '../internal/idempotency-keys';
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
  // A retry of the same content keeps its key for as long as the form lives.
  const keys = useRef(createIdempotencyKeys());

  const create = useCallback(
    async (req: CreatePlatformTenantRequest) => {
      setLoading(true);
      setError(null);
      try {
        const created = await tenant.create(req, keys.current.keyFor(req));
        keys.current.done();
        return created;
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
