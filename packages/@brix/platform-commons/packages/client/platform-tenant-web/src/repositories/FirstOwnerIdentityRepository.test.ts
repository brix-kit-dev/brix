/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { describe, expect, it, vi } from 'vitest';
import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import { createFirstOwnerIdentityRepository } from './FirstOwnerIdentityRepository';

function createHttpCapabilityMock(): HttpCapability {
  return {
    request: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
  };
}

describe('FirstOwnerIdentityRepository', () => {
  it('logs in the invitee actor through HttpCapability only', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.request).mockResolvedValue({
      data: {
        success: true,
        status: 'SELECT_TENANT',
        identityToken: 'identity-token',
        identityId: 11,
        tenants: [],
      },
      status: 200,
      statusText: 'OK',
      headers: {},
    });

    const repository = createFirstOwnerIdentityRepository(http);
    const result = await repository.loginActorForFirstOwner({
      loginId: 'owner@example.test',
      password: 'owner-password',
    });

    expect(http.request).toHaveBeenCalledWith({
      url: '/auth/login/actor',
      method: 'POST',
      data: {
        loginId: 'owner@example.test',
        password: 'owner-password',
      },
    });
    expect(result).toEqual({
      success: true,
      status: 'SELECT_TENANT',
      token: undefined,
      refreshToken: undefined,
      expiresIn: undefined,
      identityToken: 'identity-token',
      tenantOptions: [],
      identityId: '11',
      displayName: undefined,
      primaryRole: undefined,
      roles: undefined,
      permissions: undefined,
      mustChangePassword: undefined,
      requireMfa: undefined,
    });
  });

  it('maps invalid invitee credentials to a FIRST_OWNER login-stage error', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.request).mockRejectedValue({
      name: 'HttpError',
      status: 401,
      response: {
        success: false,
        code: 'AUTH_INVALID_CREDENTIALS',
        message: 'Invalid credentials',
      },
    });

    const repository = createFirstOwnerIdentityRepository(http);

    await expect(repository.loginActorForFirstOwner({
      loginId: 'owner@example.test',
      password: 'wrong-password',
    })).rejects.toThrow('FIRST_OWNER_INVITEE_LOGIN_INVALID');
  });
});
