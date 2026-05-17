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
 * @file Mobile Tenant Constants
 * @description Centralizes all constant values used across the mobile tenant module.
 * No hardcoded strings elsewhere — all keys, prefixes, and defaults are defined here.
 *
 * @module @brix-sdk/platform-tenant-mobile/constants
 * @version 3.2.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — implementation constants.
 *
 * @since 3.2.0
 */

// =========================================
// Secure Storage Keys
// =========================================

/**
 * Prefix for all tenant-related secure storage keys.
 * Uses Keychain (iOS) / Keystore (Android) via SecureStorageCapability.
 */
export const SECURE_STORAGE_PREFIX = 'brix:tenant' as const;

/**
 * Key for storing the last selected tenant ID in secure storage.
 */
export const LAST_TENANT_KEY = `${SECURE_STORAGE_PREFIX}:lastTenantId` as const;

/**
 * Key for storing the cached tenant list in secure storage.
 */
export const CACHED_TENANTS_KEY = `${SECURE_STORAGE_PREFIX}:cachedTenants` as const;

/**
 * Key for storing tenant branding data in secure storage.
 */
export const CACHED_BRANDING_KEY = `${SECURE_STORAGE_PREFIX}:branding` as const;

// =========================================
// API Paths
// =========================================

/**
 * Default base URL for tenant API endpoints.
 */
export const DEFAULT_API_BASE_URL = '/api/v1/tenant' as const;

// =========================================
// Push Notification Keys
// =========================================

/**
 * Data payload key for tenant ID in push notifications.
 * Backend sets this field when sending tenant-scoped push messages.
 */
export const PUSH_TENANT_ID_KEY = 'tenant_id' as const;

/**
 * Data payload key for auto-switch flag in push notifications.
 * When true, tapping the notification auto-switches to the target tenant.
 */
export const PUSH_AUTO_SWITCH_KEY = 'auto_switch_tenant' as const;

/**
 * Topic prefix for tenant-scoped push notification subscriptions.
 * Format: `tenant_{tenantId}` for FCM topic subscription.
 */
export const PUSH_TOPIC_PREFIX = 'tenant_' as const;

// =========================================
// Feature Flag Defaults
// =========================================

/**
 * Default feature flag key for mobile-specific branding.
 */
export const FEATURE_MOBILE_BRANDING = 'mobile:branding' as const;

/**
 * Default feature flag key for push notification tenant routing.
 */
export const FEATURE_PUSH_TENANT_ROUTING = 'mobile:push:tenantRouting' as const;

// =========================================
// Timing Constants
// =========================================

/**
 * Cache TTL for tenant list (in milliseconds).
 * Default: 5 minutes.
 */
export const TENANT_CACHE_TTL_MS = 5 * 60 * 1000;

/**
 * Cache TTL for branding data (in milliseconds).
 * Default: 30 minutes (branding changes infrequently).
 */
export const BRANDING_CACHE_TTL_MS = 30 * 60 * 1000;
