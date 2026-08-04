/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import { PLATFORM_TENANT_API } from '../constants';

export interface AcceptFirstOwnerInvitationRequest {
  readonly invitationToken: string;
}

export interface AcceptFirstOwnerInvitationOptions {
  readonly identityToken?: string;
}

export interface FirstOwnerAcceptanceDto {
  readonly tenantId: string;
  readonly memberId: string;
  readonly profileId: string;
  readonly tenantStatus: string;
}

export interface TenantInvitationRepository {
  acceptFirstOwnerInvitation(
    req: AcceptFirstOwnerInvitationRequest,
    options?: AcceptFirstOwnerInvitationOptions,
  ): Promise<FirstOwnerAcceptanceDto>;
}

interface BackendFirstOwnerAcceptanceDto {
  readonly tenantId?: string | number;
  readonly memberId?: string | number;
  readonly profileId?: string | number;
  readonly tenantStatus?: string;
}

export function createTenantInvitationRepository(
  http: HttpCapability,
): TenantInvitationRepository {
  return {
    async acceptFirstOwnerInvitation(req, options) {
      if (options?.identityToken) {
        const response = await http.request<BackendFirstOwnerAcceptanceDto>({
          url: PLATFORM_TENANT_API.FIRST_OWNER_ACCEPT,
          method: 'POST',
          headers: {
            Authorization: `Bearer ${options.identityToken}`,
          },
          data: { invitationToken: req.invitationToken },
        });
        return normalizeFirstOwnerAcceptance(response.data);
      }

      const response = await http.post<unknown>(
        PLATFORM_TENANT_API.FIRST_OWNER_ACCEPT,
        { invitationToken: req.invitationToken },
      );
      return normalizeFirstOwnerAcceptance(response as BackendFirstOwnerAcceptanceDto);
    },
  };
}

function normalizeFirstOwnerAcceptance(
  response: BackendFirstOwnerAcceptanceDto,
): FirstOwnerAcceptanceDto {
  return {
    tenantId: String(response.tenantId ?? ''),
    memberId: String(response.memberId ?? ''),
    profileId: String(response.profileId ?? ''),
    tenantStatus: response.tenantStatus ?? '',
  };
}
