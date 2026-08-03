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
 * @file useTenantSwitch Hook — Tenant Switching Logic
 * @description Encapsulates tenant switching workflow including validation,
 * state transitions, and post-switch data refresh. Used by TenantSwitcher
 * and TenantSelector components.
 *
 * @module @brix-sdk/platform-tenant-web/useTenantSwitch
 * @version 3.1.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — implementation hook wrapping TenantCapability.switchTenant().
 *
 * [Workflow]
 * 1. Validate target tenant is in availableTenants
 * 2. Set switching state
 * 3. Call TenantCapability.switchTenant()
 * 4. Persist last tenant ID through Runtime PluginStateCapability
 * 5. Clear switching state
 *
 * @since 3.1.0
 */

import { useState, useCallback } from 'react';
import { useTenant } from './useTenant';
import { useLastTenant } from './useLastTenant';

/**
 * Return type for useTenantSwitch hook.
 */
export interface UseTenantSwitchResult {
  /** Whether a tenant switch is currently in progress */
  isSwitching: boolean;

  /** Error from the last switch attempt, null if successful */
  switchError: Error | null;

  /**
   * Switch to a different tenant.
   *
   * @param targetTenantId - the ID of the tenant to switch to
   * @throws Error if the target tenant is not in availableTenants
   */
  switchTo: (targetTenantId: string) => Promise<void>;
}

/**
 * Hook that provides tenant switching functionality.
 *
 * Wraps the TenantCapability.switchTenant() with additional logic
 * for validation, loading state, error handling, and Runtime state
 * persistence of the last selected tenant.
 *
 * @example
 * ```tsx
 * function TenantSwitchButton({ targetId }: { targetId: string }) {
 *   const { switchTo, isSwitching, switchError } = useTenantSwitch();
 *
 *   return (
 *     <button onClick={() => switchTo(targetId)} disabled={isSwitching}>
 *       {isSwitching ? 'Switching...' : 'Switch Tenant'}
 *       {switchError && <span className="error">{switchError.message}</span>}
 *     </button>
 *   );
 * }
 * ```
 *
 * @returns UseTenantSwitchResult
 */
export function useTenantSwitch(): UseTenantSwitchResult {
  const { tenant, availableTenants, switchTenant } = useTenant();
  const { setLastTenantId } = useLastTenant();
  const [isSwitching, setIsSwitching] = useState(false);
  const [switchError, setSwitchError] = useState<Error | null>(null);

  const switchTo = useCallback(async (targetTenantId: string) => {
    if (!targetTenantId) {
      setSwitchError(new Error('Target tenant ID is required'));
      return;
    }

    // Don't switch to current tenant
    if (tenant?.id === targetTenantId) {
      return;
    }

    // Validate target is in available list
    const isAvailable = availableTenants.some(t => t.id === targetTenantId);
    if (!isAvailable) {
      setSwitchError(new Error(`Tenant ${targetTenantId} is not available for this user`));
      return;
    }

    setIsSwitching(true);
    setSwitchError(null);

    try {
      await switchTenant(targetTenantId);
      setLastTenantId(targetTenantId);
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error));
      setSwitchError(err);
    } finally {
      setIsSwitching(false);
    }
  }, [tenant, availableTenants, switchTenant, setLastTenantId]);

  return {
    isSwitching,
    switchError,
    switchTo,
  };
}
