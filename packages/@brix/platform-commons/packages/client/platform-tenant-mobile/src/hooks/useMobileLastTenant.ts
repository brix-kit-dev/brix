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
 * @file useMobileLastTenant — Last Tenant Persistence & Recovery Hook
 * @description Reads and writes the user's last selected tenant ID to
 * SecureStorageCapability. Mobile counterpart of useLastTenant() from
 * platform-tenant-web, but stores in Keychain/Keystore instead of localStorage.
 *
 * @module @brix-sdk/platform-tenant-mobile/hooks/useMobileLastTenant
 * @version 3.2.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — utility hook for tenant selection persistence.
 *
 * [Storage]
 * Uses SecureStorageCapability (Keychain/Keystore) via MobileTenantStorage.
 * Key: 'brix:tenant:lastTenantId'
 *
 * @since 3.2.0
 */

import { useState, useCallback, useEffect } from 'react';
import type { SecureStorageCapability } from '@brix-sdk/runtime-sdk-api-mobile';
import { MobileTenantStorage } from '../services/MobileTenantStorage';

/**
 * Return type for useMobileLastTenant hook.
 */
export interface UseMobileLastTenantResult {
  /** The last tenant ID from secure storage, or null if not set */
  lastTenantId: string | null;

  /** Whether the storage read is in progress */
  isLoading: boolean;

  /**
   * Persists the given tenant ID as the last selected tenant.
   *
   * @param tenantId the tenant ID to persist
   */
  setLastTenantId: (tenantId: string) => Promise<void>;

  /**
   * Clears the stored last tenant ID.
   */
  clearLastTenantId: () => Promise<void>;
}

/**
 * Hook for reading and writing the last selected tenant ID on mobile.
 *
 * On mount, reads the value from SecureStorageCapability (async).
 * Provides setter and clearer methods for managing the persisted tenant ID.
 *
 * @param secureStorage the SecureStorageCapability from DeviceCapability
 *
 * @example
 * ```tsx
 * function AppBootstrap({ secureStorage }: { secureStorage: SecureStorageCapability }) {
 *   const { lastTenantId, isLoading } = useMobileLastTenant(secureStorage);
 *   const { availableTenants, switchTenant } = useMobileTenant();
 *
 *   useEffect(() => {
 *     if (!isLoading && lastTenantId && availableTenants.some(t => t.id === lastTenantId)) {
 *       switchTenant(lastTenantId);
 *     }
 *   }, [lastTenantId, isLoading, availableTenants]);
 *
 *   // ...
 * }
 * ```
 *
 * @returns UseMobileLastTenantResult
 */
export function useMobileLastTenant(secureStorage: SecureStorageCapability): UseMobileLastTenantResult {
  const [lastTenantId, setLastTenantIdState] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const storage = new MobileTenantStorage(secureStorage);

  // Read last tenant ID on mount
  useEffect(() => {
    let cancelled = false;

    (async () => {
      const storedId = await storage.getLastTenantId();
      if (!cancelled) {
        setLastTenantIdState(storedId);
        setIsLoading(false);
      }
    })();

    return () => { cancelled = true; };
  }, [secureStorage]);

  const setLastTenantId = useCallback(async (tenantId: string) => {
    await storage.setLastTenantId(tenantId);
    setLastTenantIdState(tenantId);
  }, [storage]);

  const clearLastTenantId = useCallback(async () => {
    await storage.clearLastTenantId();
    setLastTenantIdState(null);
  }, [storage]);

  return {
    lastTenantId,
    isLoading,
    setLastTenantId,
    clearLastTenantId,
  };
}
