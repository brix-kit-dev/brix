/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 *
 * [Stability Reform v1.0 — C-3.4 Sample Refactor]
 * Migrated from manual `useState(loading|error|data)` triple to
 * `usePageState` from @brix-sdk/runtime-sdk-react. Eliminates duplicated
 * boilerplate, centralises last-write-wins discard, and is unmount-safe.
 */

import { useCallback, useEffect, useState } from 'react';
import { usePageState } from '@brix-sdk/runtime-sdk-react';
import { useRepositories } from './useRepositories';
import type {
  AuditLogQuery,
  Page,
  PlatformAuditLogDto,
} from '../types';

export interface UseAuditLogResult {
  data: Page<PlatformAuditLogDto> | null;
  loading: boolean;
  error: Error | null;
  query: AuditLogQuery;
  setQuery: (q: AuditLogQuery) => void;
  refresh: () => Promise<void>;
}

export function useAuditLog(
  initial: AuditLogQuery = { page: 0, size: 50, sort: 'createdAt,desc' },
): UseAuditLogResult {
  const { audit } = useRepositories();
  const page = usePageState<Page<PlatformAuditLogDto>>();
  const [query, setQuery] = useState<AuditLogQuery>(initial);

  const load = useCallback(async (): Promise<void> => {
    await page.run(() => audit.query(query));
  }, [audit, page, query]);

  useEffect(() => {
    void load();
  }, [load]);

  return {
    data: page.data,
    loading: page.isLoading,
    error: page.error,
    query,
    setQuery,
    refresh: load,
  };
}
