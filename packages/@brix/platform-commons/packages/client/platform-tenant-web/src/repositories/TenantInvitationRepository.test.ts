/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { describe, expect, it, vi } from 'vitest';
import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import { createTenantInvitationRepository } from './TenantInvitationRepository';

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

describe('TenantInvitationRepository', () => {
  it('accepts FIRST_OWNER invitations through HttpCapability only', async () => {
    const http = createHttpCapabilityMock();
    vi.mocked(http.post).mockResolvedValue({
      tenantId: 42,
      memberId: 7,
      profileId: 9,
      tenantStatus: 'ACTIVE',
    });

    const repository = createTenantInvitationRepository(http);
    const result = await repository.acceptFirstOwnerInvitation({
      invitationToken: 'raw-invite-token',
    });

    expect(http.post).toHaveBeenCalledWith(
      '/tenant/first-owner-invitations/accept',
      { invitationToken: 'raw-invite-token' },
    );
    expect(result).toEqual({
      tenantId: '42',
      memberId: '7',
      profileId: '9',
      tenantStatus: 'ACTIVE',
    });
  });
});

