/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
import type {
  HttpCapability,
  LoginResult,
  LoginStep,
  TenantOption,
} from '@brix-sdk/runtime-sdk-api-web';

/**
 * Auth API error returned by the repository layer.
 */
export class AuthApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly payload?: unknown;

  constructor(message: string, status: number, code?: string, payload?: unknown) {
    super(message);
    this.name = 'AuthApiError';
    this.status = status;
    this.code = code;
    this.payload = payload;
  }
}

export interface LoginRequestPayload {
  loginId: string;
  password: string;
  rememberMe?: boolean;
}

export interface SelectTenantPayload {
  tenantId?: string;
  selectionTicket?: string;
  principalId?: string;
}

export interface ChangePasswordPayload {
  oldPassword: string;
  newPassword: string;
}

export interface AuthApi {
  login(payload: LoginRequestPayload): Promise<LoginResult>;
  loginActor(payload: LoginRequestPayload): Promise<LoginResult>;
  loginSubject(payload: LoginRequestPayload): Promise<LoginResult>;
  selectTenant(payload: SelectTenantPayload, identityToken: string): Promise<LoginResult>;
  selectContext(payload: SelectTenantPayload, identityToken: string): Promise<LoginResult>;
  switchContext(payload: SelectTenantPayload, identityToken: string): Promise<LoginResult>;
  refresh(refreshToken: string): Promise<LoginResult>;
  changePassword(payload: ChangePasswordPayload, accessToken: string): Promise<void>;
  loginWithGoogleIdToken(idToken: string): Promise<LoginResult>;
}

export interface CreateAuthApiOptions {
  baseUrl?: string;
  httpCapability?: HttpCapability;
}

interface LoginResponseDtoRaw {
  success?: boolean;
  step?: LoginStep;
  status?: LoginStep;
  accessToken?: string;
  refreshToken?: string;
  expiresIn?: number;
  identityToken?: string;
  tenants?: TenantOption[];
  tenantOptions?: TenantOption[];
  identityId?: string | number;
  displayName?: string;
  email?: string;
  primaryRole?: string;
  roles?: string[];
  permissions?: string[];
  mustChangePassword?: boolean;
  mfaRequired?: boolean;
}

function normaliseBaseUrl(baseUrl: string | undefined): string {
  if (!baseUrl) return '';
  return baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl;
}

function toLoginResult(raw: LoginResponseDtoRaw): LoginResult {
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

export function createAuthApi(options: CreateAuthApiOptions = {}): AuthApi {
  const base = normaliseBaseUrl(options.baseUrl);
  const http = options.httpCapability;

  async function postJson<T>(
    path: string,
    body: unknown,
    bearerToken?: string,
  ): Promise<T> {
    if (!http) {
      throw new AuthApiError(
        'HttpCapability is required for auth API calls',
        500,
        'HTTP_CAPABILITY_REQUIRED',
      );
    }
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    };
    if (bearerToken) {
      headers.Authorization = `Bearer ${bearerToken}`;
    }
    try {
      const response = await http.request<T>({
        url: `${base}${path}`,
        method: 'POST',
        headers,
        data: body ?? {},
      });
      return response.data;
    } catch (e) {
      if (e instanceof AuthApiError) {
        throw e;
      }
      throw new AuthApiError(
        e instanceof Error ? e.message : String(e),
        0,
        'AUTH_HTTP_ERROR',
        e,
      );
    }
  }

  const api: AuthApi = {
    async login(payload) {
      return toLoginResult(await postJson<LoginResponseDtoRaw>('/api/auth/login', payload));
    },
    async loginActor(payload) {
      return toLoginResult(await postJson<LoginResponseDtoRaw>('/api/auth/login/actor', payload));
    },
    async loginSubject(payload) {
      return toLoginResult(await postJson<LoginResponseDtoRaw>('/api/auth/login/subject', payload));
    },
    async selectTenant(payload, identityToken) {
      if (!identityToken) {
        throw new AuthApiError('identityToken is required', 400, 'IDENTITY_TOKEN_REQUIRED');
      }
      return toLoginResult(await postJson<LoginResponseDtoRaw>(
        '/api/auth/select-tenant',
        { tenantId: payload.tenantId },
        identityToken,
      ));
    },
    async selectContext(payload, identityToken) {
      if (!identityToken) {
        throw new AuthApiError('identityToken is required', 400, 'IDENTITY_TOKEN_REQUIRED');
      }
      if (!payload.selectionTicket) {
        throw new AuthApiError('selectionTicket is required', 400, 'SELECTION_TICKET_REQUIRED');
      }
      return toLoginResult(await postJson<LoginResponseDtoRaw>(
        '/api/auth/select-context',
        { selectionTicket: payload.selectionTicket },
        identityToken,
      ));
    },
    async switchContext(payload, identityToken) {
      return api.selectContext(payload, identityToken);
    },
    async refresh(refreshToken) {
      return toLoginResult(await postJson<LoginResponseDtoRaw>('/api/auth/refresh', { refreshToken }));
    },
    async changePassword(payload, accessToken) {
      if (!accessToken) {
        throw new AuthApiError('accessToken is required', 401, 'ACCESS_TOKEN_REQUIRED');
      }
      await postJson<void>('/api/auth/change-password', payload, accessToken);
    },
    async loginWithGoogleIdToken(idToken) {
      return toLoginResult(await postJson<LoginResponseDtoRaw>(
        '/api/auth/login/google',
        { idToken },
      ));
    },
  };

  return api;
}
