import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { createPlatformAuthService, type AuthUser } from './createPlatformAuthService';

function createStorageMock(): Storage {
  const values = new Map<string, string>();
  return {
    get length() {
      return values.size;
    },
    clear: vi.fn(() => values.clear()),
    getItem: vi.fn((key: string) => values.get(key) ?? null),
    key: vi.fn((index: number) => Array.from(values.keys())[index] ?? null),
    removeItem: vi.fn((key: string) => values.delete(key)),
    setItem: vi.fn((key: string, value: string) => values.set(key, value)),
  };
}

const authUser: AuthUser = {
  id: 'admin-1',
  username: 'super-admin',
  email: 'admin@example.com',
  name: 'Super Admin',
  role: 'platform-admin',
  roles: ['platform-admin'],
  permissions: ['platform.admin.read'],
};

describe('createPlatformAuthService refreshToken', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createStorageMock());
    vi.stubGlobal('sessionStorage', createStorageMock());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('accepts the current backend accessToken refresh contract', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      accessToken: 'access-new',
      refreshToken: 'refresh-new',
      user: authUser,
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);

    const authService = createPlatformAuthService({ storageMode: 'session' });
    authService.installSession(authUser, 'access-old', 'refresh-old');

    await expect(authService.refreshToken()).resolves.toBe(true);
    expect(authService.getToken()).toBe('access-new');
    expect(sessionStorage.getItem('auth_refresh_token')).toBe('refresh-new');
    expect(JSON.parse(localStorage.getItem('auth_user') ?? '{}')).toMatchObject({
      id: 'admin-1',
      role: 'platform-admin',
    });
  });

  it('keeps compatibility with the legacy token refresh contract', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      success: true,
      token: 'legacy-access-new',
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);

    const authService = createPlatformAuthService({ storageMode: 'session' });
    authService.installSession(authUser, 'legacy-access-old', 'legacy-refresh-old');

    await expect(authService.refreshToken()).resolves.toBe(true);
    expect(authService.getToken()).toBe('legacy-access-new');
  });
});