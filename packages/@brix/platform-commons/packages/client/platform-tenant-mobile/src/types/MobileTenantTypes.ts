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
 * @file Mobile Tenant Types — Domain-specific types for mobile tenant module
 * @description Defines types beyond the SDK contract that are specific to the
 * mobile tenant implementation (UI props, storage keys, internal state).
 *
 * @module @brix-sdk/platform-tenant-mobile/types
 * @version 3.2.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — implementation-specific types.
 *
 * @since 3.2.0
 */

import type { TenantInfo, TenantBranding } from '@brix-sdk/runtime-sdk-api-mobile';

// =========================================
// Provider Props
// =========================================

/**
 * Props for MobileTenantProvider component.
 */
export interface MobileTenantProviderProps {
  /** Child components */
  children: React.ReactNode;

  /**
   * Function to get the current auth token.
   * Used for API calls to the tenant service.
   */
  getAuthToken: () => Promise<string | null>;

  /**
   * Base URL for tenant API endpoints.
   * Default: '/api/v1/tenant'
   */
  apiBaseUrl?: string;

  /**
   * Callback fired when tenant changes.
   */
  onTenantChange?: (tenant: TenantInfo | null) => void;

  /**
   * Callback fired on tenant loading error.
   */
  onError?: (error: Error) => void;
}

// =========================================
// Mobile Tenant Context
// =========================================

/**
 * Full tenant context exposed by useMobileTenant() hook.
 */
export interface MobileTenantContext {
  /** Current tenant, null if not yet loaded */
  readonly tenant: TenantInfo | null;

  /** Whether tenant data is currently loading */
  readonly isLoading: boolean;

  /** Last error from tenant operations, null if no error */
  readonly error: Error | null;

  /** List of tenants the current user can access */
  readonly availableTenants: readonly TenantInfo[];

  /** Tenant branding for the current tenant */
  readonly branding: TenantBranding | null;

  /**
   * Switch to a different tenant.
   *
   * Persists the selection to secure storage and refreshes tenant data.
   *
   * @param tenantId the ID of the tenant to switch to
   */
  switchTenant(tenantId: string): Promise<void>;

  /**
   * Refresh the current tenant data from the server.
   */
  refreshTenant(): Promise<void>;

  /**
   * Check if a feature is enabled for the current tenant.
   *
   * @param featureKey the feature key to check
   * @returns true if the feature is enabled
   */
  isFeatureEnabled(featureKey: string): boolean;
}

// =========================================
// Tenant Selector Props (Full-screen)
// =========================================

/**
 * Props for MobileTenantSelector component.
 */
export interface MobileTenantSelectorProps {
  /** Optional title text (default: "Select Organization") */
  title?: string;

  /** Optional subtitle text */
  subtitle?: string;

  /** Style for the outer container */
  style?: Record<string, unknown>;

  /** Style for each tenant item */
  itemStyle?: Record<string, unknown>;

  /** Callback invoked after successful tenant selection */
  onSelected?: (tenantId: string) => void;
}

// =========================================
// Push Notification Tenant Routing
// =========================================

/**
 * Parsed tenant information from a push notification payload.
 */
export interface PushTenantPayload {
  /** Target tenant ID from the push notification */
  readonly tenantId: string;

  /** Whether the notification should trigger a tenant switch */
  readonly autoSwitch: boolean;
}
