/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useEffect } from 'react';
import { usePageState } from '@brix-sdk/runtime-sdk-react';
import { useRepositories } from './useRepositories';
import type { InstallationQuotaDto } from '../types';

export interface UseInstallationQuotaResult {
  data: InstallationQuotaDto | null;
  loading: boolean;
  error: Error | null;
  refresh: () => Promise<void>;
}

export function useInstallationQuota(): UseInstallationQuotaResult {
  const { license } = useRepositories();
  const state = usePageState<InstallationQuotaDto>();

  const load = useCallback(async (): Promise<void> => {
    await state.run(() => license.getInstallationQuota());
  }, [license, state]);

  useEffect(() => {
    void load();
  }, [load]);

  return {
    data: state.data,
    loading: state.isLoading,
    error: state.error,
    refresh: load,
  };
}