/**
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file useTenantLoginFlow
 * @description Phase 3 login flow hook for Actor / Subject tracks.
 *
 * Page -> Hook -> Repository -> HttpCapability:
 * - Pages call this hook only.
 * - The hook creates the auth repository with useHttp().
 * - The repository uses HttpCapability and never fetch/axios directly.
 */

import { useCallback, useMemo } from 'react';
import { useHttp, useNavigation } from '@brix-sdk/runtime-sdk-react';
import type { LoginResult, TenantOption } from '@brix-sdk/runtime-sdk-api-web';
import type { LoginFormData } from '../components/LoginForm';
import { createAuthApi } from '../services/authApi';
import { useLoginCoordinator } from './useLoginCoordinator';

export interface UseTenantLoginFlowOptions {
  readonly track: 'actor' | 'subject';
  readonly apiBaseUrl?: string;
  readonly completePath?: string;
  readonly changePasswordPath?: string;
  readonly onComplete?: (result: LoginResult) => void | Promise<void>;
}

export interface UseTenantLoginFlowResult {
  readonly step: 'CREDENTIALS' | 'SELECT_TENANT' | 'COMPLETE';
  readonly loading: boolean;
  readonly error: string | null;
  readonly tenantOptions: readonly TenantOption[];
  readonly result: LoginResult | null;
  readonly submitCredentials: (data: LoginFormData) => Promise<void>;
  readonly selectOption: (option: TenantOption) => Promise<void>;
  readonly selectTicket: (selectionTicket: string) => Promise<void>;
  readonly reset: () => void;
}

/**
 * Coordinates one complete tenant login flow.
 */
export function useTenantLoginFlow(
  options: UseTenantLoginFlowOptions,
): UseTenantLoginFlowResult {
  const http = useHttp();
  const navigation = useNavigation();

  const authApi = useMemo(
    () => createAuthApi({ baseUrl: options.apiBaseUrl, httpCapability: http }),
    [http, options.apiBaseUrl],
  );

  const coordinator = useLoginCoordinator({
    authApi,
    loginTrack: options.track,
    onComplete: async (result) => {
      await options.onComplete?.(result);
      const target = result.mustChangePassword
        ? options.changePasswordPath
        : options.completePath;
      if (target) {
        navigation.navigate(target, { replace: true });
      }
    },
  });

  const submitCredentials = useCallback(
    async (data: LoginFormData) => {
      await coordinator.submitCredentials({
        loginId: data.username,
        password: data.password,
        rememberMe: data.rememberMe,
      });
    },
    [coordinator],
  );

  const selectTicket = useCallback(
    async (selectionTicket: string) => {
      await coordinator.selectTenant({ selectionTicket });
    },
    [coordinator],
  );

  const selectOption = useCallback(
    async (option: TenantOption) => {
      if (option.selectionTicket) {
        await selectTicket(option.selectionTicket);
        return;
      }
      await coordinator.selectTenant({ tenantId: option.tenantId });
    },
    [coordinator, selectTicket],
  );

  return {
    step: coordinator.step,
    loading: coordinator.loading,
    error: coordinator.error,
    tenantOptions: coordinator.tenantOptions,
    result: coordinator.result,
    submitCredentials,
    selectOption,
    selectTicket,
    reset: coordinator.reset,
  };
}
