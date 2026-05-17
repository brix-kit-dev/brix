/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useEffect, useState } from 'react';
import { useRepositories } from './useRepositories';
import type {
  Page,
  PlatformTenantDto,
  TenantQuery,
} from '../types';

export interface UsePlatformTenantListResult {
  data: Page<PlatformTenantDto> | null;
  loading: boolean;
  error: Error | null;
  query: TenantQuery;
  setQuery: (q: TenantQuery) => void;
  refresh: () => Promise<void>;
}

export function usePlatformTenantList(
  initial: TenantQuery = { page: 0, size: 20, sort: 'createdAt,desc' },
): UsePlatformTenantListResult {
  const { tenant } = useRepositories();
  const [data, setData] = useState<Page<PlatformTenantDto> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [query, setQuery] = useState<TenantQuery>(initial);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await tenant.list(query));
    } catch (e) {
      setError(e instanceof Error ? e : new Error(String(e)));
    } finally {
      setLoading(false);
    }
  }, [tenant, query]);

  useEffect(() => {
    void load();
  }, [load]);

  return { data, loading, error, query, setQuery, refresh: load };
}
