/**
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * @file authApi.ts
 * @description S5 — Auth REST 端点 fetch 包装。封装 `/api/auth/*` 五个端点：
 *   - POST /api/auth/login
 *   - POST /api/auth/select-tenant     （Bearer = identityToken）
 *   - POST /api/auth/refresh
 *   - POST /api/auth/change-password   （Bearer = access token）
 *   - POST /api/auth/login/google      （Google ID Token 联邦登录）
 *
 * 返回值统一为 {@link LoginResult}（与后端 LoginResponseDto 字段对齐），
 * 由调用方自行决定如何映射到 AuthCapability / 状态机。
 *
 * 设计原则：
 * - 不引入额外的 HTTP client 依赖，使用浏览器原生 `fetch`。
 * - 非 2xx 响应统一抛出 {@link AuthApiError}，携带状态码和后端错误码。
 * - 不做缓存、不做重试 — 这些是上层 AuthCapability 的职责。
 *
 * @module @brix-sdk/platform-auth-ui-web/services/authApi
 * @since 3.2.0
 */
import type {
  LoginResult,
  LoginStep,
  TenantOption,
} from '@brix-sdk/runtime-sdk-api-web';

/**
 * Auth API 错误，对应后端 4xx/5xx 响应。
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
  tenantId: string;
  /** Optional principal/role hint when an identity has multiple principals in one tenant. */
  principalId?: string;
}

export interface ChangePasswordPayload {
  oldPassword: string;
  newPassword: string;
}

export interface AuthApi {
  login(payload: LoginRequestPayload): Promise<LoginResult>;
  selectTenant(payload: SelectTenantPayload, identityToken: string): Promise<LoginResult>;
  refresh(refreshToken: string): Promise<LoginResult>;
  changePassword(payload: ChangePasswordPayload, accessToken: string): Promise<void>;
  loginWithGoogleIdToken(idToken: string): Promise<LoginResult>;
}

export interface CreateAuthApiOptions {
  /**
   * REST base URL, e.g. `''`（同源） or `'https://api.example.com'`. Trailing
   * slash is tolerated.
   */
  baseUrl?: string;
  /**
   * Custom fetch implementation (defaults to global `fetch`). Useful for
   * tests or for injecting interceptors.
   */
  fetchImpl?: typeof fetch;
}

/**
 * Backend `LoginResponseDto` raw shape (snake-case-friendly camelCase, as
 * Spring serialises records). Only fields S5 cares about are typed here;
 * unknown fields are passed through.
 */
interface LoginResponseDtoRaw {
  status?: LoginStep;
  accessToken?: string;
  refreshToken?: string;
  expiresIn?: number;
  identityToken?: string;
  tenantOptions?: TenantOption[];
  identityId?: string | number;
  displayName?: string;
  email?: string;
  primaryRole?: string;
  roles?: string[];
  permissions?: string[];
  mustChangePassword?: boolean;
  mfaRequired?: boolean;
  platformAdminMode?: boolean;
}

function normaliseBaseUrl(baseUrl: string | undefined): string {
  if (!baseUrl) return '';
  return baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl;
}

function toLoginResult(raw: LoginResponseDtoRaw): LoginResult {
  return {
    success: true,
    status: raw.status ?? 'COMPLETE',
    token: raw.accessToken,
    refreshToken: raw.refreshToken,
    expiresIn: raw.expiresIn,
    identityToken: raw.identityToken,
    tenantOptions: raw.tenantOptions,
    identityId: raw.identityId != null ? String(raw.identityId) : undefined,
    displayName: raw.displayName,
    primaryRole: raw.primaryRole,
    roles: raw.roles,
    permissions: raw.permissions,
    mustChangePassword: raw.mustChangePassword,
    requireMfa: raw.mfaRequired,
    platformAdminMode: raw.platformAdminMode,
  };
}

async function readError(response: Response): Promise<AuthApiError> {
  let payload: unknown;
  let code: string | undefined;
  let message = `Auth API ${response.status} ${response.statusText}`;
  try {
    payload = await response.json();
    if (payload && typeof payload === 'object') {
      const obj = payload as Record<string, unknown>;
      if (typeof obj.code === 'string') code = obj.code;
      if (typeof obj.message === 'string') message = obj.message;
    }
  } catch {
    // Body is not JSON or empty — keep default message.
  }
  return new AuthApiError(message, response.status, code, payload);
}

/**
 * 构造 AuthApi 实例。该工厂不持有任何状态；token 由调用方按方法传入。
 */
export function createAuthApi(options: CreateAuthApiOptions = {}): AuthApi {
  const base = normaliseBaseUrl(options.baseUrl);
  const fetchImpl = options.fetchImpl ?? fetch;

  async function postJson<T>(
    path: string,
    body: unknown,
    bearerToken?: string,
  ): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    };
    if (bearerToken) {
      headers.Authorization = `Bearer ${bearerToken}`;
    }
    const response = await fetchImpl(`${base}${path}`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body ?? {}),
      credentials: 'include',
    });
    if (!response.ok) {
      throw await readError(response);
    }
    if (response.status === 204) {
      return undefined as unknown as T;
    }
    return (await response.json()) as T;
  }

  return {
    async login(payload) {
      const raw = await postJson<LoginResponseDtoRaw>('/api/auth/login', payload);
      return toLoginResult(raw);
    },
    async selectTenant(payload, identityToken) {
      if (!identityToken) {
        throw new AuthApiError(
          'identityToken is required for select-tenant',
          400,
          'IDENTITY_TOKEN_REQUIRED',
        );
      }
      const raw = await postJson<LoginResponseDtoRaw>(
        '/api/auth/select-tenant',
        payload,
        identityToken,
      );
      return toLoginResult(raw);
    },
    async refresh(refreshToken) {
      const raw = await postJson<LoginResponseDtoRaw>('/api/auth/refresh', {
        refreshToken,
      });
      return toLoginResult(raw);
    },
    async changePassword(payload, accessToken) {
      if (!accessToken) {
        throw new AuthApiError(
          'accessToken is required for change-password',
          401,
          'ACCESS_TOKEN_REQUIRED',
        );
      }
      await postJson<void>('/api/auth/change-password', payload, accessToken);
    },
    async loginWithGoogleIdToken(idToken) {
      const raw = await postJson<LoginResponseDtoRaw>('/api/auth/login/google', {
        idToken,
      });
      return toLoginResult(raw);
    },
  };
}
