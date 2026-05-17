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
 * @file useMobileTenantSwitch — Tenant Switching Logic Hook
 * @description Encapsulates tenant switching workflow for mobile including
 * validation, state transitions, secure storage persistence, and post-switch
 * data refresh.
 *
 * @module @brix-sdk/platform-tenant-mobile/hooks/useMobileTenantSwitch
 * @version 3.2.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — implementation hook wrapping switchTenant().
 *
 * @since 3.2.0
 */

import { useState, useCallback } from 'react';
import { useMobileTenant } from './useMobileTenant';

/**
 * Return type for useMobileTenantSwitch hook.
 */
export interface UseMobileTenantSwitchResult {
  /** Whether a tenant switch is currently in progress */
  isSwitching: boolean;

  /** Error from the last switch attempt, null if successful */
  switchError: Error | null;

  /**
   * Switch to a different tenant.
   *
   * @param targetTenantId the ID of the tenant to switch to
   */
  switchTo: (targetTenantId: string) => Promise<void>;
}

/**
 * Hook that provides tenant switching functionality.
 *
 * Wraps the MobileTenantContext.switchTenant() with additional logic
 * for validation, loading state, and error handling.
 *
 * @example
 * ```tsx
 * function TenantSwitchButton({ targetId }: { targetId: string }) {
 *   const { switchTo, isSwitching, switchError } = useMobileTenantSwitch();
 *
 *   return (
 *     <TouchableOpacity onPress={() => switchTo(targetId)} disabled={isSwitching}>
 *       <Text>{isSwitching ? 'Switching...' : 'Switch Tenant'}</Text>
 *       {switchError && <Text style={{ color: 'red' }}>{switchError.message}</Text>}
 *     </TouchableOpacity>
 *   );
 * }
 * ```
 *
 * @returns UseMobileTenantSwitchResult
 */
export function useMobileTenantSwitch(): UseMobileTenantSwitchResult {
  const { tenant, availableTenants, switchTenant } = useMobileTenant();
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
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error));
      setSwitchError(err);
    } finally {
      setIsSwitching(false);
    }
  }, [tenant, availableTenants, switchTenant]);

  return {
    isSwitching,
    switchError,
    switchTo,
  };
}
