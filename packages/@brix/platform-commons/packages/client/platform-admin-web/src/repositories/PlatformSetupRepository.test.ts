import { describe, expect, it, vi } from 'vitest';
import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import { createPlatformSetupRepository } from './PlatformSetupRepository';

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

describe('createPlatformSetupRepository', () => {
  it('keeps successful setup validation aligned with the backend DTO', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.get).mockResolvedValueOnce({
      valid: true,
      identityId: '974180454301175808',
      email: 'root-admin@example.invalid',
      username: 'root-admin',
      purpose: 'INITIAL_SETUP',
      expiresAt: '2026-05-23T08:58:15.200589Z',
    });

    const repository = createPlatformSetupRepository(http);
    const result = await repository.validate('setup-token');

    expect(result.valid).toBe(true);
    expect(http.get).toHaveBeenCalledWith('/platform/auth/setup/validate', {
      token: 'setup-token',
    });
  });

  it('posts the neutral token field required by backend TOTP setup init', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.post).mockResolvedValueOnce({
      challengeId: 'challenge-id',
      otpauthUri: 'otpauth://totp/Brix:root-admin',
    });

    const repository = createPlatformSetupRepository(http);
    const result = await repository.initTotp('setup-token');

    expect(result.challengeId).toBe('challenge-id');
    expect(http.post).toHaveBeenCalledWith('/platform/auth/setup/totp/init', {
      token: 'setup-token',
    });
  });

  it('posts setup completion without exposing the setupToken field name', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.post).mockResolvedValueOnce({ activated: true });

    const repository = createPlatformSetupRepository(http);
    await repository.complete({
      token: 'setup-token',
      challengeId: 'challenge-id',
      password: 'StrongPassword!2026',
      totpCode: '123456',
    });

    expect(http.post).toHaveBeenCalledWith('/platform/auth/setup/complete', {
      token: 'setup-token',
      challengeId: 'challenge-id',
      password: 'StrongPassword!2026',
      totpCode: '123456',
    });
  });
});
