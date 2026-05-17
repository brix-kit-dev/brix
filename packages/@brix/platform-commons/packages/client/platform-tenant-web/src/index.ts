/**
 * @fileoverview Platform Tenant Web - Multi-tenant Context Module
 * 
 * This module provides multi-tenant support for React applications in the Brix platform.
 * It implements the tenant management capabilities defined in the v3.0.9 blueprint.
 * 
 * @module @brix/platform-tenant-web
 * @version 1.0.0
 * @see v3.0.9-runtime-shell-architecture-blueprint.md - Section 14: Multi-tenant Architecture
 * 
 * @example
 * ```tsx
 * // 1. Wrap your app with TenantProvider
 * import { TenantProvider } from '@brix/platform-tenant-web';
 * 
 * function App() {
 *   return (
 *     <TenantProvider>
 *       <YourApp />
 *     </TenantProvider>
 *   );
 * }
 * 
 * // 2. Use the useTenant hook in components
 * import { useTenant } from '@brix/platform-tenant-web';
 * 
 * function UserList() {
 *   const { tenant, isLoading, error } = useTenant();
 *   
 *   if (isLoading) return <Loading />;
 *   if (error) return <Error message={error.message} />;
 *   if (!tenant) return <NoAccess />;
 *   
 *   // Use tenant.id for API calls
 *   return <UserTable tenantId={tenant.id} />;
 * }
 * ```
 */

// Context Types
export type {
  Tenant,
  TenantStatus,
  TenantFeature,
  TenantContext,
  TenantProviderProps,
} from './TenantContext';

// Provider Component
export { TenantProvider, TenantReactContext } from './TenantProvider';

// Hooks
export {
  useTenant,
  useTenantId,
  useRequiredTenantId,
  useFeatureEnabled,
  useTenantData,
} from './useTenant';

// v3.1.0 Phase 3 — Tenant Configuration & Interaction
export { useTenantSwitch, type UseTenantSwitchResult } from './useTenantSwitch';
export { useLastTenant, type UseLastTenantResult } from './useLastTenant';
export { TenantSelector, type TenantSelectorProps } from './TenantSelector';
export { TenantSwitcher, type TenantSwitcherProps } from './TenantSwitcher';

// Capability Implementation (v3.1.0 Phase 1.4)
export {
  TenantCapabilityImpl,
  type TenantCapabilityConfig,
} from './TenantCapabilityImpl';

// Phase 2 / C-4 — ViewMode Capability (v3.3.0)
export {
  ViewModeCapabilityImpl,
  type ViewModeCapabilityConfig,
} from './ViewModeCapabilityImpl';
export {
  PlatformAdminBanner,
  type PlatformAdminBannerProps,
  type PlatformAdminBannerStyle,
} from './components/PlatformAdminBanner';
export {
  RequireViewMode,
  type RequireViewModeProps,
} from './components/RequireViewMode';

// API Client
export {
  TenantRepository,
  DefaultTenantHttpClient,
  type TenantHttpClient,
} from './api/TenantRepository';
