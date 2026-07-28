import { describe, expect, it, vi } from 'vitest';
import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import { createPlatformTenantRepository } from './PlatformTenantRepository';

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

describe('createPlatformTenantRepository FIRST_OWNER invitations', () => {
  it('loads the current FIRST_OWNER invitation status through HttpCapability only', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.get).mockResolvedValueOnce({
      invitationId: '8841',
      tenantId: '42',
      inviteeEmail: 'owner@example.invalid',
      status: 'PENDING',
      expiresAt: '2026-07-28T11:00:00Z',
      token: 'must-not-render',
      inviteUrl: 'https://console.example.invalid/?token=must-not-render',
    });

    const repository = createPlatformTenantRepository(http);
    const invitation = await repository.currentFirstOwnerInvitation('42');

    expect(http.get).toHaveBeenCalledWith(
      '/platform/tenants/42/first-owner-invitations/current',
    );
    expect(invitation).toEqual({
      invitationId: '8841',
      tenantId: '42',
      inviteeEmail: 'owner@example.invalid',
      status: 'PENDING',
      expiresAt: '2026-07-28T11:00:00Z',
    });
  });

  it('maps an empty current invitation response to null', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.get).mockResolvedValueOnce(null);

    const repository = createPlatformTenantRepository(http);
    await expect(repository.currentFirstOwnerInvitation('42')).resolves.toBeNull();
  });

  it('creates a FIRST_OWNER invitation through HttpCapability only', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.post).mockResolvedValueOnce({
      invitationId: '8842',
      tenantId: '42',
      inviteeEmail: 'owner@example.invalid',
      status: 'PENDING',
      expiresAt: '2026-07-28T12:00:00Z',
    });

    const repository = createPlatformTenantRepository(http);
    const invitation = await repository.createFirstOwnerInvitation('42', {
      inviteeEmail: 'owner@example.invalid',
      inviteBaseUrl: 'https://console.example.invalid/invitations/first-owner',
      locale: 'en-US',
    });

    expect(http.post).toHaveBeenCalledWith(
      '/platform/tenants/42/first-owner-invitations',
      {
        inviteeEmail: 'owner@example.invalid',
        inviteBaseUrl: 'https://console.example.invalid/invitations/first-owner',
        locale: 'en-US',
      },
    );
    expect(invitation).toEqual({
      invitationId: '8842',
      tenantId: '42',
      inviteeEmail: 'owner@example.invalid',
      status: 'PENDING',
      expiresAt: '2026-07-28T12:00:00Z',
    });
  });

  it('resends and revokes without exposing invitation token material', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.post).mockResolvedValueOnce({
      id: '8843',
      tenantId: '42',
      inviteeEmail: 'owner@example.invalid',
      status: 'PENDING',
      expiresAt: '2026-07-28T13:00:00Z',
      token: 'must-not-render',
      inviteUrl: 'https://console.example.invalid/?token=must-not-render',
    });
    vi.mocked(http.delete).mockResolvedValueOnce(undefined);

    const repository = createPlatformTenantRepository(http);
    const invitation = await repository.resendFirstOwnerInvitation('42', {
      inviteBaseUrl: 'https://console.example.invalid/invitations/first-owner',
      locale: 'en-US',
    });
    await repository.revokeFirstOwnerInvitation('42', invitation.invitationId);

    expect(invitation).toEqual({
      invitationId: '8843',
      tenantId: '42',
      inviteeEmail: 'owner@example.invalid',
      status: 'PENDING',
      expiresAt: '2026-07-28T13:00:00Z',
    });
    expect(http.delete).toHaveBeenCalledWith(
      '/platform/tenants/42/first-owner-invitations/8843',
    );
  });
});
