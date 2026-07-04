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
 * @file useTenant Hook — Multi-Tenancy Capability React Hook
 * @description Provides React components with access to the TenantCapability
 * from RuntimeContext using the v3.1.3 Actor/Subject access-context contract.
 *
 * @module @brix-sdk/runtime-sdk-react/hooks/useTenant
 * @version 3.1.3
 *
 * [Architecture Layer]
 * React binding layer — bridges TenantCapability contract to React components.
 *
 * [Architecture Compliance]
 * - View code consumes this hook instead of reading JWT/header state.
 * - Actor-only context switching is exposed only on the actor branch.
 * - Subject sessions do not expose available contexts or switch operations.
 *
 * @since 3.1.0
 * @see TenantCapability - Contract in runtime-sdk-api-web
 */

import { useMemo, useState, useEffect, useCallback } from 'react';
import type {
  ActorTenantAccessContext,
  CurrentTenantAccessContext,
  SubjectTenantAccessContext,
  TenantCapability,
  TenantInfo,
} from '@brix-sdk/runtime-sdk-api-web';
import { useRuntimeContext } from './useRuntimeContext';

/**
 * TenantCapability type identifier.
 * Matches the Symbol used in bootstrap registration.
 * @internal
 */
const TenantCapabilityType = Symbol.for('TenantCapability');

/**
 * Shared return fields for the useTenant hook.
 */
export interface UseTenantBaseResult {
  /** Current tenant ID, or null if not established */
  tenantId: string | null;

  /** Full tenant information, or null if not loaded */
  tenant: TenantInfo | null;

  /** Current actor/subject access context, or null before context selection */
  currentContext: CurrentTenantAccessContext | null;

  /**
   * Check if a feature is enabled for the current tenant.
   *
   * @param featureKey - feature key (e.g. 'booking:advanced')
   * @returns true if enabled
   */
  isFeatureEnabled: (featureKey: string) => boolean;

  /** The raw TenantCapability instance for advanced usage */
  capability: TenantCapability;
}

/**
 * Actor tenant hook result.
 *
 * Only actor sessions can enumerate and switch contexts.
 */
export interface UseTenantActorResult extends UseTenantBaseResult {
  readonly role: 'actor';
  readonly currentContext: ActorTenantAccessContext;
  readonly availableContexts: readonly ActorTenantAccessContext[];
  readonly switchContext: (contextId: string) => Promise<void>;
}

/**
 * Subject tenant hook result.
 *
 * Subject sessions are single-context. The result intentionally does not expose
 * availableContexts or switchContext.
 */
export interface UseTenantSubjectResult extends UseTenantBaseResult {
  readonly role: 'subject';
  readonly currentContext: SubjectTenantAccessContext;
}

/**
 * No tenant-scoped access context is currently available.
 */
export interface UseTenantNoneResult extends UseTenantBaseResult {
  readonly role: 'none';
  readonly currentContext: null;
}

export type UseTenantResult =
  | UseTenantActorResult
  | UseTenantSubjectResult
  | UseTenantNoneResult;

/**
 * Multi-Tenancy Capability Hook.
 *
 * Resolves TenantCapability from RuntimeContext and provides reactive
 * actor/subject tenant state for React components. Automatically re-renders
 * when the tenant context changes.
 *
 * @returns UseTenantResult — tenant state and actor-only operations
 * @throws Error if used outside RuntimeContextProvider
 * @throws Error if TenantCapability is not registered
 * @since 3.1.0
 */
export function useTenant(): UseTenantResult {
  const context = useRuntimeContext();

  const tenantCapability = useMemo(() => {
    const capability = context.getCapability<TenantCapability>(TenantCapabilityType);
    if (!capability) {
      throw new Error(
        '[runtime-sdk-react] TenantCapability is not registered in RuntimeContext. ' +
        'Ensure the Host registers TenantCapability in bootstrap via ' +
        'runtime.registerCapability(TenantCapabilityType, tenantCapability).',
      );
    }
    return capability;
  }, [context]);

  const [tenantId, setTenantId] = useState<string | null>(
    () => tenantCapability.getCurrentTenantId(),
  );
  const [tenant, setTenant] = useState<TenantInfo | null>(
    () => tenantCapability.getCurrentTenant(),
  );
  const [currentContext, setCurrentContext] =
    useState<CurrentTenantAccessContext | null>(
      () => tenantCapability.getCurrentContext?.() ?? null,
    );

  useEffect(() => {
    const unsubscribe = tenantCapability.onTenantChange((event) => {
      setTenantId(event.tenantId);
      setTenant(event.tenant);
      setCurrentContext(event.context ?? tenantCapability.getCurrentContext?.() ?? null);
    });
    return unsubscribe;
  }, [tenantCapability]);

  const isFeatureEnabled = useCallback(
    (featureKey: string) => tenantCapability.isFeatureEnabled(featureKey),
    [tenantCapability],
  );

  const switchContext = useCallback(
    async (targetContextId: string) => {
      if (!targetContextId) {
        throw new Error('[runtime-sdk-react] contextId is required for context switching.');
      }
      if (!tenantCapability.switchContext) {
        throw new Error(
          '[runtime-sdk-react] TenantCapability.switchContext is not registered. ' +
          'Phase 3 context switching requires an Actor contextId endpoint.',
        );
      }
      await tenantCapability.switchContext(targetContextId);
      setTenantId(tenantCapability.getCurrentTenantId());
      setTenant(tenantCapability.getCurrentTenant());
      setCurrentContext(tenantCapability.getCurrentContext?.() ?? null);
    },
    [tenantCapability],
  );

  const base: UseTenantBaseResult = {
    tenantId,
    tenant,
    currentContext,
    isFeatureEnabled,
    capability: tenantCapability,
  };

  if (currentContext?.role === 'actor') {
    return {
      ...base,
      role: 'actor',
      currentContext,
      availableContexts: tenantCapability.getAvailableContexts?.() ?? [],
      switchContext,
    };
  }

  if (currentContext?.role === 'subject') {
    return {
      ...base,
      role: 'subject',
      currentContext,
    };
  }

  return {
    ...base,
    role: 'none',
    currentContext: null,
  };
}
