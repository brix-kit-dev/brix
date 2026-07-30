/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/* @vitest-environment jsdom */

import { act, renderHook, waitFor } from '@testing-library/react';
import {
  AuthCapabilityType,
  AuthSessionInstallProvider,
  type AuthCapability,
  type LoginCredentials,
  type LoginResult,
  type RuntimeContext,
} from '@brix-sdk/runtime-sdk-api-web';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { usePlatformLoginTotp } from './usePlatformLoginTotp';
import type {
  PlatformLoginTotpRequest,
  PlatformLoginTotpResponse,
} from '../types';

const mocks = vi.hoisted(() => ({
  loginTotp: vi.fn<[PlatformLoginTotpRequest], Promise<PlatformLoginTotpResponse>>(),
  capabilityLogin: vi.fn<[LoginCredentials], Promise<LoginResult>>(),
}));

vi.mock('./useRepositories', () => ({
  useRepositories: () => ({
    auth: {
      loginTotp: mocks.loginTotp,
    },
  }),
}));

vi.mock('@brix-sdk/runtime-sdk-react', () => ({
  useRuntimeContext: () => runtimeContext(),
}));

describe('usePlatformLoginTotp', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('installs the completed platform session through AuthCapability', async () => {
    mocks.loginTotp.mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      expiresIn: 3600,
      platformRole: 'PLATFORM_SUPER_ADMIN',
      permissions: ['platform:tenant:read'],
      identityId: '42',
      email: 'admin@example.com',
      displayName: 'Platform Admin',
    });
    mocks.capabilityLogin.mockResolvedValue({ success: true });

    const { result } = renderHook(() => usePlatformLoginTotp());

    await act(async () => {
      await result.current.loginTotp({
        mfaChallengeToken: 'challenge-token',
        totpCode: '123456',
      });
    });

    await waitFor(() => expect(result.current.error).toBeNull());
    expect(mocks.loginTotp).toHaveBeenCalledWith({
      mfaChallengeToken: 'challenge-token',
      totpCode: '123456',
    });
    expect(mocks.capabilityLogin).toHaveBeenCalledWith({
      provider: AuthSessionInstallProvider,
      token: 'access-token',
      refreshToken: 'refresh-token',
      contextKind: 'platform',
      subjectId: '42',
      username: undefined,
      email: 'admin@example.com',
      displayName: 'Platform Admin',
      primaryRole: 'PLATFORM_SUPER_ADMIN',
      roles: ['PLATFORM_SUPER_ADMIN'],
      permissions: ['platform:tenant:read'],
      expiresIn: 3600,
    });
  });
});

function runtimeContext(): RuntimeContext {
  return {
    moduleId: 'platform-admin',
    tenantId: '',
    getCapability: (capabilityType) => capabilityType === AuthCapabilityType
      ? authCapability()
      : undefined,
  };
}

function authCapability(): AuthCapability {
  return {
    getCurrentUser: () => null,
    isAuthenticated: () => false,
    login: mocks.capabilityLogin,
    logout: async () => undefined,
    hasPermission: () => false,
    hasRole: () => false,
    getToken: () => null,
    getVerifiedSession: () => ({
      state: 'anonymous',
      activeContext: null,
      permissions: [],
    }),
    getActiveContext: () => null,
    getVerifiedPlatformContext: () => null,
    getVerifiedActorContext: () => null,
    getVerifiedSubjectContext: () => null,
    getVerifiedBootstrapContext: () => null,
    canAccessRoute: () => ({ allowed: false, reason: 'anonymous' }),
    getState: () => ({
      isAuthenticated: false,
      user: null,
      tenant: null,
      loading: false,
      dataScopes: [],
    }),
  };
}
