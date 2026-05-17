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
 * from RuntimeContext, following the same pattern as useAuth, useI18n, etc.
 *
 * @module @brix-sdk/runtime-sdk-react/hooks/useTenant
 * @version 3.1.0
 *
 * [Architecture Layer]
 * React binding layer — bridges TenantCapability contract to React components.
 *
 * [Architecture Compliance]
 * - Blueprint v3.0.9 Section 14.1: Three-layer identity model
 *   Identity (useAuth) → Membership (useTenant) → Profile (useAuth.permissions)
 * - Blueprint Constraint 2: Plugins only depend on Capability Contract
 * - Phase 1.4: Formal useTenant hook exported from runtime-sdk-react
 *
 * [Migration Guide]
 * Before (platform-tenant-web private hook — requires TenantProvider):
 *   import { useTenant } from '@brix-sdk/platform-tenant-web';
 *
 * After (SDK-standard hook — resolves from RuntimeContext):
 *   import { useTenant } from '@brix-sdk/runtime-sdk-react';
 *
 * [Design Decision]
 * This hook resolves TenantCapability from RuntimeContext, consistent with
 * useAuth, useI18n, and useHttp hooks. The Host registers TenantCapabilityImpl
 * during bootstrap, and plugins consume it through this hook without knowing
 * how tenant resolution is implemented.
 *
 * @since 3.1.0
 * @see TenantCapability - Contract in runtime-sdk-api-web
 * @see TenantCapabilityImpl - Implementation in platform-tenant-web
 */

import { useMemo, useState, useEffect, useCallback } from 'react';
import type {
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

// ============================================================================
// Return Type
// ============================================================================

/**
 * Return type for the useTenant hook.
 *
 * Provides convenient access to tenant context, feature flags,
 * and tenant switching for React components.
 */
export interface UseTenantResult {
  /** Current tenant ID, or null if not established */
  tenantId: string | null;

  /** Full tenant information, or null if not loaded */
  tenant: TenantInfo | null;

  /** All tenants available to the current user */
  availableTenants: readonly TenantInfo[];

  /**
   * Check if a feature is enabled for the current tenant.
   *
   * @param featureKey - feature key (e.g. 'booking:advanced')
   * @returns true if enabled
   */
  isFeatureEnabled: (featureKey: string) => boolean;

  /**
   * Switch to a different tenant.
   * After switching, tenant state is automatically refreshed.
   *
   * @param tenantId - target tenant ID
   */
  switchTenant: (tenantId: string) => Promise<void>;

  /** The raw TenantCapability instance for advanced usage */
  capability: TenantCapability;
}

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * Multi-Tenancy Capability Hook.
 *
 * Resolves TenantCapability from RuntimeContext and provides reactive
 * tenant state for React components. Automatically re-renders when
 * the tenant context changes (e.g., after switchTenant).
 *
 * @example
 * ```tsx
 * function TenantHeader() {
 *   const { tenantId, tenant, switchTenant } = useTenant();
 *
 *   return (
 *     <header>
 *       <span>Tenant: {tenant?.name ?? 'Loading...'}</span>
 *       <button onClick={() => switchTenant('other-tenant')}>
 *         Switch
 *       </button>
 *     </header>
 *   );
 * }
 * ```
 *
 * @example
 * ```tsx
 * function FeatureGate() {
 *   const { isFeatureEnabled } = useTenant();
 *
 *   if (isFeatureEnabled('analytics:export')) {
 *     return <ExportButton />;
 *   }
 *   return <UpgradePrompt />;
 * }
 * ```
 *
 * @returns UseTenantResult — tenant state and operations
 * @throws Error if used outside RuntimeContextProvider
 * @throws Error if TenantCapability is not registered
 * @since 3.1.0
 */
export function useTenant(): UseTenantResult {
  const context = useRuntimeContext();

  // Resolve TenantCapability from RuntimeContext (memoized per context instance)
  const tenantCapability = useMemo(() => {
    const capability = context.getCapability<TenantCapability>(TenantCapabilityType);
    if (!capability) {
      throw new Error(
        '[runtime-sdk-react] TenantCapability is not registered in RuntimeContext. ' +
        'Ensure the Host registers TenantCapability in bootstrap via ' +
        'runtime.registerCapability(TenantCapabilityType, tenantCapability).'
      );
    }
    return capability;
  }, [context]);

  // Reactive tenant state — re-renders on tenant changes
  const [tenantId, setTenantId] = useState<string | null>(
    () => tenantCapability.getCurrentTenantId()
  );
  const [tenant, setTenant] = useState<TenantInfo | null>(
    () => tenantCapability.getCurrentTenant()
  );

  // Subscribe to tenant changes for reactive updates
  useEffect(() => {
    const unsubscribe = tenantCapability.onTenantChange((event) => {
      setTenantId(event.tenantId);
      setTenant(event.tenant);
    });
    return unsubscribe;
  }, [tenantCapability]);

  // Stable reference for available tenants
  const availableTenants = useMemo(
    () => tenantCapability.getAvailableTenants(),
    [tenantCapability, tenantId], // Refresh when tenant changes
  );

  // Stable callback references
  const isFeatureEnabled = useCallback(
    (featureKey: string) => tenantCapability.isFeatureEnabled(featureKey),
    [tenantCapability],
  );

  const switchTenant = useCallback(
    (targetTenantId: string) => tenantCapability.switchTenant(targetTenantId),
    [tenantCapability],
  );

  return {
    tenantId,
    tenant,
    availableTenants,
    isFeatureEnabled,
    switchTenant,
    capability: tenantCapability,
  };
}
