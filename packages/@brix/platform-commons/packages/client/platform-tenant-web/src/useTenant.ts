/**
 * @fileoverview useTenant Hook - Primary API for Multi-tenant Context Access
 * 
 * This hook provides the main interface for components to access tenant context.
 * It follows the blueprint Section 14.5 specification for multi-tenant support.
 * 
 * @module platform-tenant-web/useTenant
 * @version 1.0.0
 * @see v3.0.9-runtime-shell-architecture-blueprint.md - Section 14.5: Multi-tenant Implementation
 */

import { useContext } from 'react';
import type { TenantContext } from './TenantContext';
import { TenantReactContext } from './TenantProvider';

/**
 * Hook to access the current tenant context.
 * 
 * Provides access to:
 * - Current tenant information
 * - Loading and error states
 * - Available tenants for multi-tenant users
 * - Feature flags
 * - Tenant switching capability
 * 
 * @returns The TenantContext with all tenant-related state and operations
 * @throws Error if used outside of TenantProvider
 * 
 * @example
 * ```tsx
 * function UserProfile() {
 *   const { tenant, isLoading, error } = useTenant();
 *   
 *   if (isLoading) return <Loading />;
 *   if (error) return <ErrorDisplay error={error} />;
 *   if (!tenant) return <NoTenantAccess />;
 *   
 *   return (
 *     <div>
 *       <h1>Welcome to {tenant.name}</h1>
 *       <p>Tenant ID: {tenant.id}</p>
 *     </div>
 *   );
 * }
 * ```
 * 
 * @example
 * ```tsx
 * // Accessing tenant ID for API calls
 * function UserList() {
 *   const { tenant } = useTenant();
 *   
 *   const { data } = useQuery({
 *     queryKey: ['users', tenant?.id],
 *     queryFn: () => fetchUsers(tenant!.id),
 *     enabled: !!tenant,
 *   });
 *   
 *   return <UserTable users={data} />;
 * }
 * ```
 * 
 * @example
 * ```tsx
 * // Feature flag checking
 * function AdvancedFeature() {
 *   const { isFeatureEnabled } = useTenant();
 *   
 *   if (!isFeatureEnabled('advanced-analytics')) {
 *     return <UpgradePrompt feature="Advanced Analytics" />;
 *   }
 *   
 *   return <AnalyticsDashboard />;
 * }
 * ```
 * 
 * @example
 * ```tsx
 * // Multi-tenant switching
 * function TenantSwitcher() {
 *   const { tenant, availableTenants, switchTenant, isLoading } = useTenant();
 *   
 *   if (availableTenants.length <= 1) {
 *     return null; // No switching available
 *   }
 *   
 *   return (
 *     <Select
 *       value={tenant?.id}
 *       onChange={(e) => switchTenant(e.target.value)}
 *       disabled={isLoading}
 *     >
 *       {availableTenants.map(t => (
 *         <Option key={t.id} value={t.id}>{t.name}</Option>
 *       ))}
 *     </Select>
 *   );
 * }
 * ```
 */
export function useTenant(): TenantContext {
  const context = useContext(TenantReactContext);
  
  if (context === null) {
    throw new Error(
      'useTenant must be used within a TenantProvider. ' +
      'Make sure your component tree is wrapped with <TenantProvider>.'
    );
  }
  
  return context;
}

/**
 * Hook to get tenant ID only, with null check.
 * 
 * A convenience hook for when you only need the tenant ID.
 * Returns null if tenant is not loaded yet.
 * 
 * @returns The current tenant ID or null if not available
 * 
 * @example
 * ```tsx
 * function ApiClient() {
 *   const tenantId = useTenantId();
 *   
 *   // tenantId will be null during loading
 *   // Handle accordingly in your API calls
 * }
 * ```
 */
export function useTenantId(): string | null {
  const { tenant } = useTenant();
  return tenant?.id ?? null;
}

/**
 * Hook to get required tenant ID.
 * 
 * Similar to useTenantId but throws if tenant is not available.
 * Use this when tenant is absolutely required for the component.
 * 
 * @returns The current tenant ID
 * @throws Error if tenant is not available
 * 
 * @example
 * ```tsx
 * function TenantRequiredPage() {
 *   const tenantId = useRequiredTenantId();
 *   
 *   // tenantId is guaranteed to be a string here
 *   return <TenantDashboard tenantId={tenantId} />;
 * }
 * ```
 */
export function useRequiredTenantId(): string {
  const { tenant, isLoading, error } = useTenant();
  
  if (isLoading) {
    throw new Error('Tenant is still loading. Consider using useTenantId() instead.');
  }
  
  if (error) {
    throw error;
  }
  
  if (!tenant) {
    throw new Error('No tenant available. User may not have tenant access.');
  }
  
  return tenant.id;
}

/**
 * Hook to check if a feature is enabled.
 * 
 * Convenience hook for feature flag checking.
 * 
 * @param featureKey - The feature key to check
 * @returns Boolean indicating if the feature is enabled
 * 
 * @example
 * ```tsx
 * function ConditionalFeature() {
 *   const isEnabled = useFeatureEnabled('new-dashboard');
 *   
 *   if (!isEnabled) {
 *     return <LegacyDashboard />;
 *   }
 *   
 *   return <NewDashboard />;
 * }
 * ```
 */
export function useFeatureEnabled(featureKey: string): boolean {
  const { isFeatureEnabled } = useTenant();
  return isFeatureEnabled(featureKey);
}

/**
 * Hook to get all tenant data at once.
 * 
 * Returns a tuple for easy destructuring with rename.
 * Useful when you need multiple pieces of tenant data.
 * 
 * @returns Tuple of [tenant, tenantId, isLoading, error]
 * 
 * @example
 * ```tsx
 * function MultiDataComponent() {
 *   const [tenant, tenantId, loading, err] = useTenantData();
 *   
 *   // Use as needed
 * }
 * ```
 */
export function useTenantData(): [
  tenant: TenantContext['tenant'],
  tenantId: string | null,
  isLoading: boolean,
  error: Error | null,
] {
  const { tenant, isLoading, error } = useTenant();
  return [tenant, tenant?.id ?? null, isLoading, error];
}
