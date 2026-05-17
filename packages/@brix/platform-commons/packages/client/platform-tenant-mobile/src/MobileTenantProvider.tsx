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
 * @file MobileTenantProvider — React Context Provider for Mobile Multi-tenant Support
 * @description Provides tenant context to the React Native component tree.
 * Handles tenant data fetching, secure storage persistence, branding,
 * and push notification topic management.
 *
 * @module @brix-sdk/platform-tenant-mobile/MobileTenantProvider
 * @version 3.2.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — React Context Provider implementation.
 *
 * [Mobile-specific Behavior]
 * - On mount: reads last tenant ID from SecureStorageCapability
 * - On switch: persists new tenant ID to SecureStorageCapability
 * - On mount/switch: re-subscribes to FCM topics for tenant routing
 * - Caches branding data locally for offline-first rendering
 * - No subdomain resolution (mobile uses token-based tenant detection)
 *
 * [Lifecycle]
 * 1. Created during mobile Host bootstrap
 * 2. Reads lastTenantId from secure storage
 * 3. Fetches current tenant + available tenants from API
 * 4. Subscribes to push notification topics
 * 5. Provides context to all child components
 *
 * @since 3.2.0
 */

import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { TenantInfo, TenantFeature, TenantBranding } from '@brix-sdk/runtime-sdk-api-mobile';
import { MobileTenantReactContext } from './MobileTenantContext';
import type { MobileTenantProviderProps, MobileTenantContext } from './types/MobileTenantTypes';
import { MobileTenantRepository, type MobileTenantHttpClient } from './services/MobileTenantRepository';
import { MobileTenantStorage } from './services/MobileTenantStorage';
import { PushNotificationService } from './services/PushNotificationService';
import type {
  SecureStorageCapability,
  PushNotificationCapability,
} from '@brix-sdk/runtime-sdk-api-mobile';
import { DEFAULT_API_BASE_URL } from './constants/MobileTenantConstants';

/**
 * Extended props with capability injections.
 *
 * The Host injects SecureStorageCapability and PushNotificationCapability
 * from the RuntimeContext during bootstrap.
 */
export interface MobileTenantProviderFullProps extends MobileTenantProviderProps {
  /** SecureStorageCapability from DeviceCapability.getSecureStorage() */
  secureStorage: SecureStorageCapability;

  /** PushNotificationCapability from RuntimeContext (optional) */
  pushCapability?: PushNotificationCapability;

  /** Custom HTTP client (optional, otherwise uses fetch with auth token) */
  httpClient?: MobileTenantHttpClient;
}

/**
 * Default HTTP client using fetch with injected auth token.
 */
class DefaultMobileHttpClient implements MobileTenantHttpClient {
  private readonly getAuthToken: () => Promise<string | null>;

  constructor(getAuthToken: () => Promise<string | null>) {
    this.getAuthToken = getAuthToken;
  }

  async get<T>(url: string): Promise<T> {
    const token = await this.getAuthToken();
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(url, { method: 'GET', headers });
    if (!response.ok) {
      throw new Error(`Tenant API GET ${url} failed: ${response.status} ${response.statusText}`);
    }
    return response.json() as Promise<T>;
  }

  async post<T>(url: string, data?: unknown): Promise<T> {
    const token = await this.getAuthToken();
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: data ? JSON.stringify(data) : undefined,
    });
    if (!response.ok) {
      throw new Error(`Tenant API POST ${url} failed: ${response.status} ${response.statusText}`);
    }
    return response.json() as Promise<T>;
  }
}

/**
 * Mobile Tenant Provider Component.
 *
 * Wraps the React Native application to provide tenant context to all
 * child components. Manages secure storage persistence, API fetching,
 * push notification topic subscriptions, and branding data.
 *
 * @example
 * ```tsx
 * <MobileTenantProvider
 *   getAuthToken={() => authService.getAccessToken()}
 *   secureStorage={deviceCapability.getSecureStorage()}
 *   pushCapability={pushCapability}
 *   onTenantChange={(t) => analytics.setTenant(t?.id)}
 * >
 *   <App />
 * </MobileTenantProvider>
 * ```
 *
 * @since 3.2.0
 */
export function MobileTenantProvider({
  children,
  getAuthToken,
  apiBaseUrl,
  onTenantChange,
  onError,
  secureStorage,
  pushCapability,
  httpClient,
}: MobileTenantProviderFullProps): React.ReactElement {
  // State
  const [tenant, setTenant] = useState<TenantInfo | null>(null);
  const [availableTenants, setAvailableTenants] = useState<TenantInfo[]>([]);
  const [features, setFeatures] = useState<TenantFeature[]>([]);
  const [branding, setBranding] = useState<TenantBranding | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  // Service instances (stable refs)
  const repository = useMemo(() => {
    const client = httpClient ?? new DefaultMobileHttpClient(getAuthToken);
    return new MobileTenantRepository(client, apiBaseUrl ?? DEFAULT_API_BASE_URL);
  }, [httpClient, getAuthToken, apiBaseUrl]);

  const storage = useMemo(
    () => new MobileTenantStorage(secureStorage),
    [secureStorage]
  );

  const pushServiceRef = useRef<PushNotificationService | null>(null);

  /**
   * Load features for a specific tenant.
   */
  const loadFeatures = useCallback(async (tenantId: string) => {
    try {
      const tenantFeatures = await repository.getTenantFeatures(tenantId);
      setFeatures(tenantFeatures);
    } catch {
      setFeatures([]);
    }
  }, [repository]);

  /**
   * Load branding for a specific tenant.
   */
  const loadBranding = useCallback(async (tenantId: string) => {
    try {
      const tenantBranding = await repository.getTenantBranding(tenantId);
      setBranding(tenantBranding);
      await storage.setCachedBranding(tenantBranding);
    } catch {
      // Fall back to cached branding
      const cached = await storage.getCachedBranding();
      setBranding(cached);
    }
  }, [repository, storage]);

  /**
   * Handle notification tap that requires tenant switch.
   */
  const handleNotificationTenantSwitch = useCallback(async (tenantId: string) => {
    try {
      const newTenant = await repository.switchTenant(tenantId);
      setTenant(newTenant);
      await storage.setLastTenantId(newTenant.id);
      await loadFeatures(newTenant.id);
      await loadBranding(newTenant.id);
      onTenantChange?.(newTenant);
    } catch (err) {
      const switchError = err instanceof Error ? err : new Error(String(err));
      setError(switchError);
      onError?.(switchError);
    }
  }, [repository, storage, loadFeatures, loadBranding, onTenantChange, onError]);

  /**
   * Load current tenant and available tenants from the server.
   * On first load, attempts to restore last tenant from secure storage.
   */
  const loadTenant = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      // Attempt to restore last tenant ID from secure storage
      const lastTenantId = await storage.getLastTenantId();

      // Load current tenant and available tenants in parallel
      const [currentTenant, tenants] = await Promise.all([
        repository.getCurrentTenant(),
        repository.getAvailableTenants(),
      ]);

      // Cache the tenant list
      await storage.setCachedTenants(tenants);

      // If a different lastTenantId exists and is in the available list, switch to it
      let effectiveTenant = currentTenant;
      if (
        lastTenantId
        && lastTenantId !== currentTenant.id
        && tenants.some((t: TenantInfo) => t.id === lastTenantId)
      ) {
        try {
          effectiveTenant = await repository.switchTenant(lastTenantId);
        } catch {
          // If switch fails, use the current tenant
          effectiveTenant = currentTenant;
        }
      }

      setTenant(effectiveTenant);
      setAvailableTenants(tenants);
      await storage.setLastTenantId(effectiveTenant.id);
      onTenantChange?.(effectiveTenant);

      // Load features and branding in parallel
      await Promise.all([
        loadFeatures(effectiveTenant.id),
        loadBranding(effectiveTenant.id),
      ]);

      // Register push token for tenant-scoped notifications
      if (pushServiceRef.current) {
        const token = await pushServiceRef.current.getPushToken();
        // Server-side topic registration via repository
        await repository.registerPushToken?.(token, tenants.map((t: TenantInfo) => t.id));
      }
    } catch (err) {
      const loadError = err instanceof Error ? err : new Error(String(err));
      setError(loadError);
      setTenant(null);
      setAvailableTenants([]);
      setFeatures([]);
      setBranding(null);
      onError?.(loadError);
    } finally {
      setIsLoading(false);
    }
  }, [repository, storage, loadFeatures, loadBranding, onTenantChange, onError]);

  /**
   * Switch to a different tenant.
   */
  const switchTenant = useCallback(async (tenantId: string): Promise<void> => {
    if (!tenantId) {
      throw new Error('Tenant ID is required');
    }

    if (tenant?.id === tenantId) {
      return;
    }

    const targetTenant = availableTenants.find(t => t.id === tenantId);
    if (!targetTenant) {
      throw new Error(`Tenant ${tenantId} is not available for current user`);
    }

    setIsLoading(true);
    setError(null);

    try {
      const newTenant = await repository.switchTenant(tenantId);
      setTenant(newTenant);
      await storage.setLastTenantId(newTenant.id);
      onTenantChange?.(newTenant);

      // Load features and branding for the new tenant
      await Promise.all([
        loadFeatures(newTenant.id),
        loadBranding(newTenant.id),
      ]);
    } catch (err) {
      const switchError = err instanceof Error ? err : new Error(String(err));
      setError(switchError);
      onError?.(switchError);
      throw switchError;
    } finally {
      setIsLoading(false);
    }
  }, [tenant, availableTenants, repository, storage, loadFeatures, loadBranding, onTenantChange, onError]);

  /**
   * Refresh current tenant data.
   */
  const refreshTenant = useCallback(async (): Promise<void> => {
    if (!tenant) {
      return loadTenant();
    }

    setIsLoading(true);
    setError(null);

    try {
      const refreshedTenant = await repository.getTenant(tenant.id);
      setTenant(refreshedTenant);
      onTenantChange?.(refreshedTenant);

      await Promise.all([
        loadFeatures(refreshedTenant.id),
        loadBranding(refreshedTenant.id),
      ]);
    } catch (err) {
      const refreshError = err instanceof Error ? err : new Error(String(err));
      setError(refreshError);
      onError?.(refreshError);
    } finally {
      setIsLoading(false);
    }
  }, [tenant, repository, loadFeatures, loadBranding, loadTenant, onTenantChange, onError]);

  /**
   * Check if a feature is enabled.
   */
  const isFeatureEnabled = useCallback((featureKey: string): boolean => {
    const feature = features.find(f => f.key === featureKey);
    return feature?.enabled ?? false;
  }, [features]);

  // Initialize push notification service
  useEffect(() => {
    if (pushCapability) {
      const pushService = new PushNotificationService(pushCapability);
      pushServiceRef.current = pushService;
      pushService.initialize(handleNotificationTenantSwitch);

      return () => {
        pushService.destroy();
        pushServiceRef.current = null;
      };
    }
    return undefined;
  }, [pushCapability, handleNotificationTenantSwitch]);

  // Load tenant on mount
  useEffect(() => {
    loadTenant();
  }, [loadTenant]);

  // Context value
  const contextValue = useMemo<MobileTenantContext>(() => ({
    tenant,
    isLoading,
    error,
    availableTenants,
    branding,
    switchTenant,
    refreshTenant,
    isFeatureEnabled,
  }), [tenant, isLoading, error, availableTenants, branding, switchTenant, refreshTenant, isFeatureEnabled]);

  return (
    <MobileTenantReactContext.Provider value={contextValue}>
      {children}
    </MobileTenantReactContext.Provider>
  );
}
