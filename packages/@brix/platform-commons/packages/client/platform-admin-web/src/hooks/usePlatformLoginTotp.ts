/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useState } from 'react';
import {
  AuthCapabilityType,
  AuthSessionInstallProvider,
  type AuthCapability,
  type AuthSessionInstallCredentials,
} from '@brix-sdk/runtime-sdk-api-web';
import { useRuntimeContext } from '@brix-sdk/runtime-sdk-react';
import { useRepositories } from './useRepositories';
import type {
  PlatformLoginTotpRequest,
  PlatformLoginTotpResponse,
} from '../types';

export interface UsePlatformLoginTotpResult {
  loading: boolean;
  error: Error | null;
  result: PlatformLoginTotpResponse | null;
  loginTotp: (req: PlatformLoginTotpRequest) => Promise<PlatformLoginTotpResponse>;
  reset: () => void;
}

export function usePlatformLoginTotp(): UsePlatformLoginTotpResult {
  const { auth } = useRepositories();
  const runtimeContext = useRuntimeContext();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [result, setResult] = useState<PlatformLoginTotpResponse | null>(null);
  const authCapability = runtimeContext.getCapability<AuthCapability>(AuthCapabilityType);

  const loginTotp = useCallback(
    async (req: PlatformLoginTotpRequest) => {
      setLoading(true);
      setError(null);
      try {
        const res = await auth.loginTotp(req);
        if (!authCapability) {
          throw new Error('AuthCapability is not registered in RuntimeContext');
        }
        await authCapability.login(toAuthSessionInstallCredentials(res));
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
    [auth, authCapability],
  );

  const reset = useCallback(() => {
    setError(null);
    setResult(null);
  }, []);

  return { loading, error, result, loginTotp, reset };
}

function toAuthSessionInstallCredentials(
  res: PlatformLoginTotpResponse,
): AuthSessionInstallCredentials {
  const token = readRequiredText(res.accessToken, 'accessToken');
  const subjectId = readRequiredText(res.identityId, 'identityId');
  const permissions = Array.isArray(res.permissions) ? [...res.permissions] : [];
  const roles = [readRequiredText(res.platformRole, 'platformRole')];
  const expiresIn = typeof res.expiresInSeconds === 'number'
    ? res.expiresInSeconds
    : res.expiresIn;

  return {
    provider: AuthSessionInstallProvider,
    token,
    refreshToken: readOptionalText(res.refreshToken),
    contextKind: 'platform',
    subjectId,
    username: readOptionalText(res.username),
    email: readOptionalText(res.email),
    displayName: readOptionalText(res.displayName),
    primaryRole: roles[0],
    roles,
    permissions,
    expiresIn,
  };
}

function readRequiredText(value: unknown, field: string): string {
  if (typeof value === 'string' && value.trim().length > 0) {
    return value.trim();
  }
  throw new Error(`Platform login response missing ${field}`);
}

function readOptionalText(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim().length > 0
    ? value.trim()
    : undefined;
}
