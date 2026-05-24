import { describe, expect, it, vi } from 'vitest';
import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import { createPlatformAuthRepository } from './PlatformAuthRepository';

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

describe('createPlatformAuthRepository', () => {
  it('posts login TOTP using the backend totpCode contract', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.post).mockResolvedValueOnce({
      accessToken: 'access-token',
      platformRole: 'PLATFORM_SUPER_ADMIN',
      permissions: [],
    });

    const repository = createPlatformAuthRepository(http);
    await repository.loginTotp({
      mfaChallengeToken: 'challenge-token',
      totpCode: '123456',
    });

    expect(http.post).toHaveBeenCalledWith('/platform/auth/login/totp', {
      mfaChallengeToken: 'challenge-token',
      totpCode: '123456',
    });
  });
});
