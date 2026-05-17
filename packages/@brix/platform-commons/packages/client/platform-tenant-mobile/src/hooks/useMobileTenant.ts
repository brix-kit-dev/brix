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
 * @file useMobileTenant — Primary Hook for Mobile Multi-tenant Context Access
 * @description Provides the main interface for React Native components to access
 * tenant context. Mobile counterpart of useTenant() from platform-tenant-web.
 *
 * @module @brix-sdk/platform-tenant-mobile/hooks/useMobileTenant
 * @version 3.2.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — React Hook for tenant context consumption.
 *
 * @since 3.2.0
 */

import { useContext } from 'react';
import type { MobileTenantContext } from '../types/MobileTenantTypes';
import { MobileTenantReactContext } from '../MobileTenantContext';

/**
 * Hook to access the current mobile tenant context.
 *
 * Provides access to:
 * - Current tenant information
 * - Loading and error states
 * - Available tenants for multi-tenant users
 * - Branding configuration
 * - Feature flags
 * - Tenant switching capability
 *
 * @returns The MobileTenantContext with all tenant-related state and operations
 * @throws Error if used outside of MobileTenantProvider
 *
 * @example
 * ```tsx
 * function TenantDashboard() {
 *   const { tenant, isLoading, error, branding } = useMobileTenant();
 *
 *   if (isLoading) return <ActivityIndicator />;
 *   if (error) return <ErrorView message={error.message} />;
 *   if (!tenant) return <NoAccessView />;
 *
 *   return (
 *     <View>
 *       {branding?.logoUrl && <Image source={{ uri: branding.logoUrl }} />}
 *       <Text>Welcome to {tenant.name}</Text>
 *     </View>
 *   );
 * }
 * ```
 */
export function useMobileTenant(): MobileTenantContext {
  const context = useContext(MobileTenantReactContext);

  if (context === null) {
    throw new Error(
      'useMobileTenant must be used within a MobileTenantProvider. ' +
      'Make sure your component tree is wrapped with <MobileTenantProvider>.'
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
 *   const tenantId = useMobileTenantId();
 *   // tenantId will be null during loading
 * }
 * ```
 */
export function useMobileTenantId(): string | null {
  const { tenant } = useMobileTenant();
  return tenant?.id ?? null;
}

/**
 * Hook to get required tenant ID.
 *
 * Throws if tenant is not available. Use this when tenant is absolutely
 * required for the component to function.
 *
 * @returns The current tenant ID
 * @throws Error if tenant is not available
 *
 * @example
 * ```tsx
 * function TenantRequiredScreen() {
 *   const tenantId = useRequiredMobileTenantId();
 *   // tenantId is guaranteed to be a string here
 *   return <TenantDashboard tenantId={tenantId} />;
 * }
 * ```
 */
export function useRequiredMobileTenantId(): string {
  const { tenant, isLoading, error } = useMobileTenant();

  if (isLoading) {
    throw new Error('Tenant is still loading. Consider using useMobileTenantId() instead.');
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
 * @param featureKey the feature key to check
 * @returns boolean indicating if the feature is enabled
 *
 * @example
 * ```tsx
 * function ConditionalFeature() {
 *   const isEnabled = useMobileFeatureEnabled('booking:advanced');
 *
 *   if (!isEnabled) return <LegacyView />;
 *   return <AdvancedView />;
 * }
 * ```
 */
export function useMobileFeatureEnabled(featureKey: string): boolean {
  const { isFeatureEnabled } = useMobileTenant();
  return isFeatureEnabled(featureKey);
}
