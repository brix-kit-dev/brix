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
 * @file useLastTenant Hook — Last Tenant Persistence & Recovery
 * @description Reads and writes the user's last selected tenant ID to
 * localStorage. Used during app bootstrap to auto-restore the last
 * tenant context without requiring the user to select again.
 *
 * @module @brix-sdk/platform-tenant-web/useLastTenant
 * @version 3.1.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — utility hook for tenant selection persistence.
 *
 * [Storage Key]
 * `brix:lastTenant` — stores the raw tenant ID string.
 *
 * @since 3.1.0
 */

import { useState, useCallback, useEffect } from 'react';

/**
 * localStorage key for the last selected tenant.
 */
const LAST_TENANT_KEY = 'brix:lastTenant';

/**
 * Return type for useLastTenant hook.
 */
export interface UseLastTenantResult {
  /** The last tenant ID from localStorage, or null if not set */
  lastTenantId: string | null;

  /**
   * Persists the given tenant ID as the last selected tenant.
   *
   * @param tenantId - the tenant ID to persist
   */
  setLastTenantId: (tenantId: string) => void;

  /**
   * Clears the stored last tenant ID.
   */
  clearLastTenantId: () => void;
}

/**
 * Hook for reading and writing the last selected tenant ID.
 *
 * On mount, reads the value from localStorage. Provides setter and
 * clearer methods for managing the persisted tenant ID.
 *
 * Commonly used during app bootstrap:
 * 1. Read lastTenantId
 * 2. If it exists and is in availableTenants, auto-switch to it
 * 3. Otherwise, prompt the user with TenantSelector
 *
 * @example
 * ```tsx
 * function AppBootstrap() {
 *   const { lastTenantId } = useLastTenant();
 *   const { availableTenants, switchTenant } = useTenant();
 *
 *   useEffect(() => {
 *     if (lastTenantId && availableTenants.some(t => t.id === lastTenantId)) {
 *       switchTenant(lastTenantId);
 *     }
 *   }, [lastTenantId, availableTenants]);
 *
 *   // ...
 * }
 * ```
 *
 * @returns UseLastTenantResult
 */
export function useLastTenant(): UseLastTenantResult {
  const [lastTenantId, setLastTenantIdState] = useState<string | null>(() => {
    try {
      return localStorage.getItem(LAST_TENANT_KEY);
    } catch {
      return null;
    }
  });

  const setLastTenantId = useCallback((tenantId: string) => {
    try {
      localStorage.setItem(LAST_TENANT_KEY, tenantId);
      setLastTenantIdState(tenantId);
    } catch {
      // localStorage may be unavailable
    }
  }, []);

  const clearLastTenantId = useCallback(() => {
    try {
      localStorage.removeItem(LAST_TENANT_KEY);
      setLastTenantIdState(null);
    } catch {
      // localStorage may be unavailable
    }
  }, []);

  // Sync with localStorage changes from other tabs
  useEffect(() => {
    const handler = (event: StorageEvent) => {
      if (event.key === LAST_TENANT_KEY) {
        setLastTenantIdState(event.newValue);
      }
    };
    window.addEventListener('storage', handler);
    return () => window.removeEventListener('storage', handler);
  }, []);

  return {
    lastTenantId,
    setLastTenantId,
    clearLastTenantId,
  };
}
