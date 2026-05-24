import { describe, expect, it, vi } from 'vitest';
import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import { createPlatformAdminRepository } from './PlatformAdminRepository';

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

describe('createPlatformAdminRepository', () => {
  it('normalizes string Snowflake admin IDs without precision loss', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.get).mockResolvedValueOnce([
      {
        adminId: '976369206184382464',
        identityId: '974180454301175808',
        username: 'qa-admin',
        email: 'qa-admin@example.invalid',
        role: 'PLATFORM_SUPER_ADMIN',
        status: 'ACTIVE',
        createdAt: '2026-05-18T12:00:00Z',
      },
    ]);

    const repository = createPlatformAdminRepository(http);
    const page = await repository.list();

    expect(page.content[0]?.id).toBe('976369206184382464');
    expect(page.content[0]?.identityId).toBe('974180454301175808');
  });

  it('uses PATCH for revoke admin because the backend returns 204', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.patch).mockResolvedValueOnce(undefined);

    const repository = createPlatformAdminRepository(http);
    await repository.revoke('976369206184382464', { reason: 'QA revoke' });

    expect(http.patch).toHaveBeenCalledWith(
      '/platform/admins/976369206184382464/revoke',
      { reason: 'QA revoke' },
    );
    expect(http.post).not.toHaveBeenCalled();
  });

  it('uses POST for operator password reset', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.post).mockResolvedValueOnce({
      setupLinkSent: true,
    });

    const repository = createPlatformAdminRepository(http);
    await repository.resetPassword('976369206184382464');

    expect(http.post).toHaveBeenCalledWith(
      '/platform/admins/976369206184382464/reset-password',
      {},
    );
  });
});