/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import type { HttpCapability, LoginResult, LoginStep, TenantOption } from '@brix-sdk/runtime-sdk-api-web';
import { PLATFORM_AUTH_API } from '../constants';

export interface FirstOwnerIdentityLoginRequest {
  readonly loginId: string;
  readonly password: string;
}

export interface FirstOwnerIdentityRepository {
  loginActorForFirstOwner(
    req: FirstOwnerIdentityLoginRequest,
  ): Promise<LoginResult>;
}

interface LoginResponseDtoRaw {
  readonly success?: boolean;
  readonly step?: LoginStep;
  readonly status?: LoginStep;
  readonly accessToken?: string;
  readonly refreshToken?: string;
  readonly expiresIn?: number;
  readonly identityToken?: string;
  readonly tenants?: TenantOption[];
  readonly tenantOptions?: TenantOption[];
  readonly identityId?: string | number;
  readonly displayName?: string;
  readonly email?: string;
  readonly primaryRole?: string;
  readonly roles?: string[];
  readonly permissions?: string[];
  readonly mustChangePassword?: boolean;
  readonly mfaRequired?: boolean;
}

export function createFirstOwnerIdentityRepository(
  http: HttpCapability,
): FirstOwnerIdentityRepository {
  return {
    async loginActorForFirstOwner(req) {
      try {
        const response = await http.request<LoginResponseDtoRaw>({
          url: PLATFORM_AUTH_API.ACTOR_LOGIN,
          method: 'POST',
          data: {
            loginId: req.loginId,
            password: req.password,
          },
        });
        return normalizeLoginResult(response.data);
      } catch (cause) {
        throw normalizeInviteeLoginError(cause);
      }
    },
  };
}

function normalizeLoginResult(raw: LoginResponseDtoRaw): LoginResult {
  return {
    success: raw.success ?? true,
    status: raw.step ?? raw.status ?? 'COMPLETE',
    token: raw.accessToken,
    refreshToken: raw.refreshToken,
    expiresIn: raw.expiresIn,
    identityToken: raw.identityToken,
    tenantOptions: raw.tenants ?? raw.tenantOptions,
    identityId: raw.identityId != null ? String(raw.identityId) : undefined,
    displayName: raw.displayName,
    primaryRole: raw.primaryRole,
    roles: raw.roles,
    permissions: raw.permissions,
    mustChangePassword: raw.mustChangePassword,
    requireMfa: raw.mfaRequired,
  };
}

interface HttpErrorLike {
  readonly status?: number;
  readonly response?: unknown;
}

interface AuthErrorResponseLike {
  readonly code?: unknown;
}

function normalizeInviteeLoginError(cause: unknown): Error {
  const code = readAuthErrorCode(cause);
  if (code === 'AUTH_INVALID_CREDENTIALS') {
    return new Error('FIRST_OWNER_INVITEE_LOGIN_INVALID');
  }
  if (code === 'AUTH_PENDING_SETUP') {
    return new Error('FIRST_OWNER_INVITEE_PENDING_SETUP');
  }
  if (code === 'AUTH_ACCOUNT_LOCKED') {
    return new Error('FIRST_OWNER_INVITEE_ACCOUNT_LOCKED');
  }
  if (code === 'AUTH_ACCOUNT_DISABLED') {
    return new Error('FIRST_OWNER_INVITEE_ACCOUNT_DISABLED');
  }
  if (isHttpErrorStatus(cause, 401)) {
    return new Error('FIRST_OWNER_INVITEE_LOGIN_INVALID');
  }
  return cause instanceof Error ? cause : new Error(String(cause));
}

function readAuthErrorCode(cause: unknown): string | null {
  if (!isRecord(cause)) return null;
  const response = (cause as HttpErrorLike).response;
  if (!isRecord(response)) return null;
  const code = (response as AuthErrorResponseLike).code;
  return typeof code === 'string' ? code : null;
}

function isHttpErrorStatus(cause: unknown, status: number): boolean {
  return isRecord(cause) && (cause as HttpErrorLike).status === status;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
