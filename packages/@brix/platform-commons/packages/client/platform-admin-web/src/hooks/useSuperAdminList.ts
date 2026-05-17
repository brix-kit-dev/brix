/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useEffect, useState } from 'react';
import { useRepositories } from './useRepositories';
import type { Page, PageRequest, PlatformAdminDto } from '../types';

/**
 * Paginated list of platform super-admins.
 *
 * Loading semantics:
 *   - `loading` = initial load OR explicit refresh.
 *   - `error` is captured and surfaced; the hook never throws to React.
 *   - `data` keeps the previous page during refresh (avoids UI flash).
 */
export interface UseSuperAdminListResult {
  data: Page<PlatformAdminDto> | null;
  loading: boolean;
  error: Error | null;
  query: PageRequest;
  setQuery: (q: PageRequest) => void;
  refresh: () => Promise<void>;
}

export function useSuperAdminList(
  initial: PageRequest = { page: 0, size: 20, sort: 'createdAt,desc' },
): UseSuperAdminListResult {
  const { admin } = useRepositories();
  const [data, setData] = useState<Page<PlatformAdminDto> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [query, setQuery] = useState<PageRequest>(initial);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await admin.list(query));
    } catch (e) {
      setError(e instanceof Error ? e : new Error(String(e)));
    } finally {
      setLoading(false);
    }
  }, [admin, query]);

  useEffect(() => {
    void load();
  }, [load]);

  return { data, loading, error, query, setQuery, refresh: load };
}
