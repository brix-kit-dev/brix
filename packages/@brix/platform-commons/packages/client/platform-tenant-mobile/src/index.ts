/**
 * @fileoverview Platform Tenant Mobile — Multi-tenant Context Module for React Native
 *
 * This module provides multi-tenant support for React Native applications in the Brix platform.
 * It implements the TenantCapability contract defined in runtime-sdk-api-mobile using
 * SecureStorageCapability (Keychain/Keystore) for token persistence and PushNotificationCapability
 * for tenant-scoped push notification routing.
 *
 * @module @brix-sdk/platform-tenant-mobile
 * @version 3.2.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — Capability Implementation for mobile.
 *
 * [Phase 5 Deliverables — v1.2 Multi-tenant Design Document]
 * - Task #32: platform-tenant-mobile module (Token Secure Storage + tenant switching + branding)
 * - Task #33: PushNotificationService (push token association + auto-switch on notification tap)
 *
 * @see v1.2-多租户基础功能完整设计方案.md — Phase 5
 * @see v3.0.9-runtime-shell-architecture-blueprint.md — Section 14: Multi-tenant Architecture
 *
 * @example
 * ```tsx
 * // 1. Wrap your app with MobileTenantProvider
 * import { MobileTenantProvider } from '@brix-sdk/platform-tenant-mobile';
 *
 * function App() {
 *   return (
 *     <MobileTenantProvider
 *       getAuthToken={() => authService.getAccessToken()}
 *       secureStorage={deviceCapability.getSecureStorage()}
 *       pushCapability={pushCapability}
 *     >
 *       <YourApp />
 *     </MobileTenantProvider>
 *   );
 * }
 *
 * // 2. Use the useMobileTenant hook in components
 * import { useMobileTenant } from '@brix-sdk/platform-tenant-mobile';
 *
 * function Dashboard() {
 *   const { tenant, isLoading, branding } = useMobileTenant();
 *
 *   if (isLoading) return <ActivityIndicator />;
 *   if (!tenant) return <NoAccessView />;
 *
 *   return (
 *     <View style={{ backgroundColor: branding?.primaryColor }}>
 *       <Text>Welcome to {tenant.name}</Text>
 *     </View>
 *   );
 * }
 * ```
 */

// Types
export type {
  MobileTenantProviderProps,
  MobileTenantContext,
  MobileTenantSelectorProps,
  PushTenantPayload,
} from './types/MobileTenantTypes';

// Context
export { MobileTenantReactContext } from './MobileTenantContext';

// Provider Component
export {
  MobileTenantProvider,
  type MobileTenantProviderFullProps,
} from './MobileTenantProvider';

// Hooks
export {
  useMobileTenant,
  useMobileTenantId,
  useRequiredMobileTenantId,
  useMobileFeatureEnabled,
} from './hooks/useMobileTenant';

export {
  useMobileTenantSwitch,
  type UseMobileTenantSwitchResult,
} from './hooks/useMobileTenantSwitch';

export {
  useMobileLastTenant,
  type UseMobileLastTenantResult,
} from './hooks/useMobileLastTenant';

export {
  useMobileTenantBranding,
  type UseMobileTenantBrandingResult,
} from './hooks/useMobileTenantBranding';

// UI Components
export { MobileTenantSelector } from './MobileTenantSelector';

// Capability Implementation
export {
  MobileTenantCapabilityImpl,
  type TenantCapabilityConfig,
} from './MobileTenantCapabilityImpl';

// Services
export { MobileTenantStorage } from './services/MobileTenantStorage';
export {
  PushNotificationService,
  type TenantNotificationTapHandler,
} from './services/PushNotificationService';
export {
  MobileTenantRepository,
  type MobileTenantHttpClient,
} from './services/MobileTenantRepository';

// Constants
export {
  SECURE_STORAGE_PREFIX,
  LAST_TENANT_KEY,
  CACHED_TENANTS_KEY,
  CACHED_BRANDING_KEY,
  DEFAULT_API_BASE_URL,
  PUSH_TENANT_ID_KEY,
  PUSH_AUTO_SWITCH_KEY,
  PUSH_TOPIC_PREFIX,
  FEATURE_MOBILE_BRANDING,
  FEATURE_PUSH_TENANT_ROUTING,
  TENANT_CACHE_TTL_MS,
  BRANDING_CACHE_TTL_MS,
} from './constants/MobileTenantConstants';
