/**
 * @fileoverview Tenant Provider - React Context Provider for Multi-tenant Support
 * 
 * Provides tenant context to the React component tree using the Context API.
 * Handles tenant data fetching, caching, and state management.
 * 
 * @module platform-tenant-web/TenantProvider
 * @version 1.0.0
 * @see v3.0.9-runtime-shell-architecture-blueprint.md - Section 14: Multi-tenant Architecture
 */

import React, {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';
import type {
  Tenant,
  TenantContext,
  TenantFeature,
  TenantProviderProps,
} from './TenantContext';
import {
  TenantRepository,
  DefaultTenantHttpClient,
  type TenantHttpClient,
} from './api/TenantRepository';

/**
 * Extended Props for TenantProvider
 */
interface TenantProviderInternalProps extends TenantProviderProps {
  /**
   * Custom HTTP client for API calls.
   * Must be backed by HttpCapability in production.
   */
  httpClient?: TenantHttpClient;
  
  /**
   * Function to get current auth token.
   * @deprecated Token injection belongs to HttpCapability.
   */
  getAuthToken?: () => string | null;
  
  /**
   * Base URL for tenant API endpoints.
   * Default: '/api/v1/tenant'
   */
  apiBaseUrl?: string;
  
  /**
   * Callback fired when tenant changes.
   */
  onTenantChange?: (tenant: Tenant | null) => void;
  
  /**
   * Callback fired on tenant loading error.
   */
  onError?: (error: Error) => void;

  /**
   * Callback fired after a successful tenant switch, before loading new tenant features.
   *
   * Use this to clear stale caches and reset plugin states from the previous tenant context.
   * Typical implementation:
   * ```tsx
   * onAfterTenantSwitch={() => {
   *   queryClient.clear();     // Clear React Query cache
   *   resetPluginStates();     // Reset all PluginState stores
   * }}
   * ```
   *
   * @since 3.2.0 — Phase 2 R16.8 switchTenant state reset
   */
  onAfterTenantSwitch?: () => void;

  /**
   * Navigation function called after tenant switch to redirect to a safe route.
   *
   * When a tenant switches, the current route may reference stale tenant-scoped data.
   * This callback navigates to a safe landing page (typically '/').
   *
   * @since 3.2.0 — Phase 2 R16.8 switchTenant state reset
   */
  navigateAfterSwitch?: (path: string) => void;
}

/**
 * React Context for tenant data
 * 
 * @internal
 */
export const TenantReactContext = createContext<TenantContext | null>(null);

/**
 * Tenant Provider Component
 * 
 * Wraps the application to provide tenant context to all child components.
 * Handles:
 * - Initial tenant loading on mount
 * - Tenant switching for multi-tenant users
 * - Feature flag management
 * - Caching and refresh logic
 * 
 * @example
 * ```tsx
 * // Basic usage
 * <TenantProvider>
 *   <App />
 * </TenantProvider>
 * 
 * // With custom configuration
 * <TenantProvider
 *   httpClient={customHttpClient}
 *   apiBaseUrl="/custom/api/tenant"
 *   onTenantChange={(tenant) => analytics.setTenant(tenant?.id)}
 *   onError={(error) => errorReporter.capture(error)}
 * >
 *   <App />
 * </TenantProvider>
 * ```
 */
export function TenantProvider({
  children,
  httpClient,
  getAuthToken,
  apiBaseUrl,
  onTenantChange,
  onError,
  onAfterTenantSwitch,
  navigateAfterSwitch,
}: TenantProviderInternalProps): React.ReactElement {
  // State
  const [tenant, setTenant] = useState<Tenant | null>(null);
  const [availableTenants, setAvailableTenants] = useState<Tenant[]>([]);
  const [features, setFeatures] = useState<TenantFeature[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  // Create repository instance
  const repository = useMemo(() => {
    void getAuthToken;
    const client = httpClient ?? new DefaultTenantHttpClient();
    return new TenantRepository(client, apiBaseUrl);
  }, [httpClient, getAuthToken, apiBaseUrl]);

  /**
   * Load tenant features
   */
  const loadFeatures = useCallback(async (tenantId: string) => {
    try {
      const tenantFeatures = await repository.getTenantFeatures(tenantId);
      setFeatures(tenantFeatures);
    } catch (err) {
      // Feature loading is non-critical, log but don't fail
      setFeatures([]);
    }
  }, [repository]);

  /**
   * Load current tenant and available tenants
   */
  const loadTenant = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      // Load current tenant and available tenants in parallel
      const [currentTenant, tenants] = await Promise.all([
        repository.getCurrentTenant(),
        repository.getAvailableTenants(),
      ]);

      setTenant(currentTenant);
      setAvailableTenants(tenants);
      onTenantChange?.(currentTenant);

      // Load features for current tenant
      await loadFeatures(currentTenant.id);
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err));
      setError(error);
      setTenant(null);
      setAvailableTenants([]);
      setFeatures([]);
      onError?.(error);
    } finally {
      setIsLoading(false);
    }
  }, [repository, loadFeatures, onTenantChange, onError]);

  /**
   * Switch to a different tenant
   *
   * After successful switch, clears stale state from the previous tenant context
   * and navigates to a safe route to prevent stale data rendering.
   * (Phase 2 — R16.8 switchTenant state reset)
   */
  const switchTenant = useCallback(async (tenantId: string): Promise<void> => {
    if (!tenantId) {
      throw new Error('Tenant ID is required');
    }

    // Check if already on this tenant
    if (tenant?.id === tenantId) {
      return;
    }

    // Validate tenant is in available list
    const targetTenant = availableTenants.find(t => t.id === tenantId);
    if (!targetTenant) {
      throw new Error(`Tenant ${tenantId} is not available for current user`);
    }

    setIsLoading(true);
    setError(null);

    try {
      const newTenant = await repository.switchTenant(tenantId);
      
      // Phase 2 R16.8: Reset all stale state from previous tenant context.
      // This must happen AFTER the token swap succeeds but BEFORE new tenant
      // data is rendered, to prevent stale cache hits across tenant boundaries.
      onAfterTenantSwitch?.();
      
      setTenant(newTenant);
      onTenantChange?.(newTenant);

      // Load features for new tenant
      await loadFeatures(newTenant.id);

      // Phase 2 R16.8: Navigate to safe route after tenant switch to prevent
      // rendering stale tenant-scoped routes (e.g., /tenant-A/cases/123).
      navigateAfterSwitch?.('/');
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err));
      setError(error);
      onError?.(error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, [tenant, availableTenants, repository, loadFeatures, onTenantChange, onError,
      onAfterTenantSwitch, navigateAfterSwitch]);

  /**
   * Refresh current tenant data
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

      // Refresh features as well
      await loadFeatures(refreshedTenant.id);
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err));
      setError(error);
      onError?.(error);
    } finally {
      setIsLoading(false);
    }
  }, [tenant, repository, loadFeatures, loadTenant, onTenantChange, onError]);

  /**
   * Check if a feature is enabled
   */
  const isFeatureEnabled = useCallback((featureKey: string): boolean => {
    const feature = features.find(f => f.key === featureKey);
    return feature?.enabled ?? false;
  }, [features]);

  // Load tenant on mount
  useEffect(() => {
    loadTenant();
  }, [loadTenant]);

  // Context value
  const contextValue = useMemo<TenantContext>(() => ({
    tenant,
    isLoading,
    error,
    availableTenants,
    features,
    isFeatureEnabled,
    switchTenant,
    refreshTenant,
  }), [
    tenant,
    isLoading,
    error,
    availableTenants,
    features,
    isFeatureEnabled,
    switchTenant,
    refreshTenant,
  ]);

  return (
    <TenantReactContext.Provider value={contextValue}>
      {children}
    </TenantReactContext.Provider>
  );
}

/**
 * Display name for debugging
 */
TenantProvider.displayName = 'TenantProvider';
