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

describe('createPlatformTenantRepository tenant creation', () => {
  it('names its key in the Idempotency-Key header', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.request).mockResolvedValueOnce({
      data: { tenantId: '42', code: 'acme', name: 'Acme', status: 'PENDING_ACTIVATION' },
      status: 200,
      statusText: 'OK',
      headers: {},
    });

    const repository = createPlatformTenantRepository(http);
    const tenant = await repository.create({ code: 'acme', name: 'Acme' }, 'key-1');

    expect(http.request).toHaveBeenCalledWith({
      url: '/platform/tenants',
      method: 'POST',
      data: { code: 'acme', name: 'Acme' },
      headers: { 'Idempotency-Key': 'key-1' },
    });
    expect(tenant.id).toBe('42');
    expect(tenant.status).toBe('PENDING_ACTIVATION');
  });
});

describe('createPlatformTenantRepository FIRST_OWNER invitations', () => {
  it('names its key in the Idempotency-Key header when it creates an invitation', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.request).mockResolvedValueOnce({
      data: {
        invitationId: '8841',
        tenantId: '42',
        inviteeEmail: 'owner@example.invalid',
        status: 'PENDING',
        expiresAt: '2026-07-28T11:00:00Z',
      },
      status: 200,
      statusText: 'OK',
      headers: {},
    });

    const repository = createPlatformTenantRepository(http);
    const invitation = await repository.createFirstOwnerInvitation(
      '42',
      { inviteeEmail: 'owner@example.invalid' },
      'key-1',
    );

    expect(http.request).toHaveBeenCalledWith({
      url: '/platform/tenants/42/first-owner-invitations',
      method: 'POST',
      data: { inviteeEmail: 'owner@example.invalid' },
      headers: { 'Idempotency-Key': 'key-1' },
    });
    expect(invitation.invitationId).toBe('8841');
  });

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
    vi.mocked(http.request).mockResolvedValueOnce({
      data: {
        invitationId: '8842',
        tenantId: '42',
        inviteeEmail: 'owner@example.invalid',
        status: 'PENDING',
        expiresAt: '2026-07-28T12:00:00Z',
      },
      status: 200,
      statusText: 'OK',
      headers: {},
    });

    const repository = createPlatformTenantRepository(http);
    const invitation = await repository.createFirstOwnerInvitation(
      '42',
      {
        inviteeEmail: 'owner@example.invalid',
        locale: 'en-US',
      },
      'key-2',
    );

    expect(http.request).toHaveBeenCalledWith({
      url: '/platform/tenants/42/first-owner-invitations',
      method: 'POST',
      data: {
        inviteeEmail: 'owner@example.invalid',
        locale: 'en-US',
      },
      headers: { 'Idempotency-Key': 'key-2' },
    });
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
    vi.mocked(http.request).mockResolvedValueOnce({
      data: {
        id: '8843',
        tenantId: '42',
        inviteeEmail: 'owner@example.invalid',
        status: 'PENDING',
        expiresAt: '2026-07-28T13:00:00Z',
        token: 'must-not-render',
        inviteUrl: 'https://console.example.invalid/?token=must-not-render',
      },
      status: 200,
      statusText: 'OK',
      headers: {},
    });
    vi.mocked(http.delete).mockResolvedValueOnce(undefined);

    const repository = createPlatformTenantRepository(http);
    const invitation = await repository.resendFirstOwnerInvitation(
      '42',
      {
        locale: 'en-US',
      },
      'key-3',
    );
    await repository.revokeFirstOwnerInvitation('42', invitation.invitationId);

    expect(http.request).toHaveBeenCalledWith({
      url: '/platform/tenants/42/first-owner-invitations/resend',
      method: 'POST',
      data: { locale: 'en-US' },
      headers: { 'Idempotency-Key': 'key-3' },
    });
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
