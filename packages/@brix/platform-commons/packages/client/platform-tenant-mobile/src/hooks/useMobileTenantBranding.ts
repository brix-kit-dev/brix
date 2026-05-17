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
 * @file useMobileTenantBranding — Tenant Branding Hook for Mobile UI
 * @description Provides access to the current tenant's branding configuration
 * for rendering tenant-specific logos, colors, and theme in React Native.
 *
 * @module @brix-sdk/platform-tenant-mobile/hooks/useMobileTenantBranding
 * @version 3.2.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — convenience hook for branding data access.
 *
 * @since 3.2.0
 */

import type { TenantBranding } from '@brix-sdk/runtime-sdk-api-mobile';
import { useMobileTenant } from './useMobileTenant';

/**
 * Return type for useMobileTenantBranding hook.
 */
export interface UseMobileTenantBrandingResult {
  /** Current tenant's branding configuration, null if not loaded */
  branding: TenantBranding | null;

  /** Whether the branding data is loading */
  isLoading: boolean;

  /** Primary color from branding, or undefined */
  primaryColor: string | undefined;

  /** Logo URL from branding, or undefined */
  logoUrl: string | undefined;
}

/**
 * Hook that provides the current tenant's branding configuration.
 *
 * Extracts branding data from the tenant context for easy consumption
 * in UI components that need to render tenant-specific visuals.
 *
 * @example
 * ```tsx
 * function BrandedHeader() {
 *   const { branding, primaryColor, logoUrl } = useMobileTenantBranding();
 *
 *   return (
 *     <View style={{ backgroundColor: primaryColor ?? '#fff' }}>
 *       {logoUrl && <Image source={{ uri: logoUrl }} style={styles.logo} />}
 *     </View>
 *   );
 * }
 * ```
 *
 * @returns UseMobileTenantBrandingResult
 */
export function useMobileTenantBranding(): UseMobileTenantBrandingResult {
  const { branding, isLoading } = useMobileTenant();

  return {
    branding,
    isLoading,
    primaryColor: branding?.primaryColor,
    logoUrl: branding?.logoUrl,
  };
}
