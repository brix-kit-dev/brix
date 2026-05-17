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
 * @file useLoginCoordinator.ts
 * @description S5+ — 登录三态机 Hook，统一编排：
 *
 *     CREDENTIALS ──login()──► (status==COMPLETE) ──► COMPLETE
 *                            └─(status==SELECT_TENANT)──► SELECT_TENANT
 *     SELECT_TENANT ──selectTenant()──► COMPLETE
 *     COMPLETE ──reset()──► CREDENTIALS
 *
 *   - 不持久化 token（持久化是 AuthCapability 的职责）
 *   - 出现 `mustChangePassword=true` 时仍标记为 COMPLETE，调用方根据
 *     `result.mustChangePassword` 路由到改密页
 *
 * @module @brix-sdk/platform-auth-ui-web/hooks/useLoginCoordinator
 * @since 3.2.0
 */
import { useCallback, useMemo, useRef, useState } from 'react';
import type {
  AuthApi,
  LoginRequestPayload,
  SelectTenantPayload,
} from '../services/authApi';
import { AuthApiError } from '../services/authApi';
import type { LoginResult, TenantOption } from '@brix-sdk/runtime-sdk-api-web';

export type LoginCoordinatorStep =
  | 'CREDENTIALS'
  | 'SELECT_TENANT'
  | 'COMPLETE';

export interface LoginCoordinatorState {
  readonly step: LoginCoordinatorStep;
  readonly loading: boolean;
  readonly error: string | null;
  /** SELECT_TENANT 阶段的租户候选列表。 */
  readonly tenantOptions: readonly TenantOption[];
  /** 完成态的最终 LoginResult（含 access/refresh token）。 */
  readonly result: LoginResult | null;
}

export interface UseLoginCoordinatorOptions {
  readonly authApi: AuthApi;
  /** 完成态回调，可在此持久化 token / 跳转。 */
  readonly onComplete?: (result: LoginResult) => void | Promise<void>;
}

export interface UseLoginCoordinatorReturn extends LoginCoordinatorState {
  /** 第一阶段：用账号密码登录。 */
  submitCredentials: (payload: LoginRequestPayload) => Promise<void>;
  /** 第二阶段：在 SELECT_TENANT 步骤选择租户。 */
  selectTenant: (
    payload: SelectTenantPayload | { tenantId: string },
  ) => Promise<void>;
  /** 重置回 CREDENTIALS 初始态。 */
  reset: () => void;
}

const INITIAL_STATE: LoginCoordinatorState = {
  step: 'CREDENTIALS',
  loading: false,
  error: null,
  tenantOptions: [],
  result: null,
};

function describeError(e: unknown): string {
  if (e instanceof AuthApiError) {
    return e.code ? `${e.code}: ${e.message}` : e.message;
  }
  if (e instanceof Error) {
    return e.message;
  }
  return String(e);
}

/**
 * 登录三态机 Hook。返回的 `state` 字段每一步都会更新，调用方据此渲染
 * 不同 UI（账号密码表单 → TenantSelector → 完成跳转）。
 */
export function useLoginCoordinator(
  options: UseLoginCoordinatorOptions,
): UseLoginCoordinatorReturn {
  const { authApi, onComplete } = options;
  const [state, setState] = useState<LoginCoordinatorState>(INITIAL_STATE);
  /** 第一阶段返回的 identityToken，用于 SELECT_TENANT bearer。 */
  const identityTokenRef = useRef<string | null>(null);

  const finishComplete = useCallback(
    async (result: LoginResult) => {
      identityTokenRef.current = null;
      setState({
        step: 'COMPLETE',
        loading: false,
        error: null,
        tenantOptions: [],
        result,
      });
      if (onComplete) {
        try {
          await onComplete(result);
        } catch (e) {
          // onComplete 失败不应回退状态机；仅在 console 暴露，调用方自己处理。
          // eslint-disable-next-line no-console
          console.error('[LoginCoordinator] onComplete handler threw:', e);
        }
      }
    },
    [onComplete],
  );

  const submitCredentials = useCallback(
    async (payload: LoginRequestPayload) => {
      setState((s) => ({ ...s, loading: true, error: null }));
      try {
        const result = await authApi.login(payload);
        if (result.status === 'SELECT_TENANT') {
          identityTokenRef.current = result.identityToken ?? null;
          if (!identityTokenRef.current) {
            throw new AuthApiError(
              'Backend returned SELECT_TENANT without identityToken',
              500,
              'IDENTITY_TOKEN_MISSING',
            );
          }
          setState({
            step: 'SELECT_TENANT',
            loading: false,
            error: null,
            tenantOptions: result.tenantOptions ?? [],
            result: null,
          });
          return;
        }
        await finishComplete(result);
      } catch (e) {
        identityTokenRef.current = null;
        setState({
          step: 'CREDENTIALS',
          loading: false,
          error: describeError(e),
          tenantOptions: [],
          result: null,
        });
      }
    },
    [authApi, finishComplete],
  );

  const selectTenant = useCallback(
    async (payload: SelectTenantPayload | { tenantId: string }) => {
      const token = identityTokenRef.current;
      if (!token) {
        setState((s) => ({
          ...s,
          error: 'identityToken missing — please re-enter credentials',
          step: 'CREDENTIALS',
        }));
        return;
      }
      setState((s) => ({ ...s, loading: true, error: null }));
      try {
        const result = await authApi.selectTenant(
          payload as SelectTenantPayload,
          token,
        );
        await finishComplete(result);
      } catch (e) {
        setState((s) => ({
          ...s,
          loading: false,
          error: describeError(e),
        }));
      }
    },
    [authApi, finishComplete],
  );

  const reset = useCallback(() => {
    identityTokenRef.current = null;
    setState(INITIAL_STATE);
  }, []);

  return useMemo(
    () => ({
      ...state,
      submitCredentials,
      selectTenant,
      reset,
    }),
    [state, submitCredentials, selectTenant, reset],
  );
}
