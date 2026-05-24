import { describe, expect, it, vi } from 'vitest';
import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import { createPlatformBootstrapRepository } from './PlatformBootstrapRepository';

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

describe('createPlatformBootstrapRepository', () => {
  it('loads the backend bootstrap status DTO without renaming fields', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.get).mockResolvedValueOnce({
      open: true,
      setupCodeExpiresAt: '2026-05-21T08:58:15.200589Z',
      completedAt: null,
    });

    const repository = createPlatformBootstrapRepository(http);
    const result = await repository.status();

    expect(result).toEqual({
      open: true,
      setupCodeExpiresAt: '2026-05-21T08:58:15.200589Z',
      completedAt: null,
    });
    expect(http.get).toHaveBeenCalledWith('/platform/bootstrap/status');
  });

  it('opens a bootstrap session with the backend token DTO', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.post).mockResolvedValueOnce({
      tokenType: 'BOOTSTRAP_SETUP',
      accessToken: 'bootstrap-session',
      expiresIn: 300,
    });

    const repository = createPlatformBootstrapRepository(http);
    const result = await repository.session({ setupCode: 'setup-code' });

    expect(result).toEqual({
      tokenType: 'BOOTSTRAP_SETUP',
      accessToken: 'bootstrap-session',
      expiresIn: 300,
    });
    expect(http.post).toHaveBeenCalledWith('/platform/bootstrap/session', {
      setupCode: 'setup-code',
    });
  });

  it('creates the first admin with an in-memory bootstrap session token', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.request).mockResolvedValueOnce({
      data: {
        id: '976369206184382464',
        identityId: '974180454301175808',
        setupLinkSent: true,
      },
      status: 200,
      statusText: 'OK',
      headers: {},
    });

    const repository = createPlatformBootstrapRepository(http);
    const result = await repository.createFirstAdmin(
      {
        username: 'root-admin',
        email: 'root-admin@example.invalid',
      },
      'bootstrap-session',
    );

    expect(result).toEqual({
      id: '976369206184382464',
      identityId: '974180454301175808',
      setupLinkSent: true,
    });
    expect(http.request).toHaveBeenCalledWith({
      url: '/platform/bootstrap/create-first-admin',
      method: 'POST',
      headers: { Authorization: 'Bearer bootstrap-session' },
      data: {
        username: 'root-admin',
        email: 'root-admin@example.invalid',
      },
    });
  });
});
