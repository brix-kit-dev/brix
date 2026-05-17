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
 * @file Tenant Capability Type Definitions — Mobile Platform
 * @description Defines the TenantCapability contract for multi-tenancy support on mobile.
 * Consistent with the web counterpart in runtime-sdk-api-web/types/tenant.ts.
 * @module @brix-sdk/runtime-sdk-api-mobile/types/tenant
 * @version 3.2.0
 *
 * [Architecture Layer]
 * Layer 2A: Capability Contract — pure interface definition, no implementation.
 *
 * [Identity Model — v3.0.9 Blueprint Section 14.1]
 * Three-layer identity model:
 *   1. Identity — "Who am I?" (AuthCapability)
 *   2. Membership — "Which tenant?" (TenantCapability)
 *   3. Profile — "What role?" (AuthCapability.permissions)
 *
 * [Design Constraints]
 * - Plugins obtain tenant info through TenantCapability or useTenant() hook
 * - Direct reading of JWT claims or HTTP headers is prohibited
 * - Tenant ID must come from authenticated context, never from user input
 * - On mobile, token storage uses SecureStorageCapability (Keychain/Keystore)
 *
 * @since 3.2.0
 */

import type { Subscription } from './common';

// =========================================
// Tenant Capability Type Identifier
// =========================================

/**
 * Tenant Capability Type Identifier.
 *
 * Used for capability registration and lookup in the Runtime Context.
 *
 * @example
 * ```typescript
 * // Registration (Host layer)
 * runtime.registerCapability(TenantCapabilityType, { provide: () => tenantCapability });
 *
 * // Lookup (Plugin layer)
 * const tenant = context.getCapability<TenantCapability>(TenantCapabilityType);
 * ```
 */
export const TenantCapabilityType = Symbol.for('TenantCapability');

// =========================================
// Tenant Status
// =========================================

/**
 * Tenant lifecycle status.
 *
 * Matches the backend TenantStatus enum for consistency.
 */
export type TenantStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'TERMINATED';

// =========================================
// Tenant Info
// =========================================

/**
 * Core tenant information exposed to plugins.
 *
 * Read-only view of the tenant data. Plugins should treat this as
 * immutable — modifications go through TenantCapability methods.
 */
export interface TenantInfo {
  /** Unique tenant identifier (UUID format) */
  readonly id: string;

  /** Human-readable tenant code (URL-friendly, unique) */
  readonly code: string;

  /** Display name of the tenant */
  readonly name: string;

  /** Current lifecycle status */
  readonly status: TenantStatus;

  /** Custom metadata/settings for the tenant */
  readonly metadata?: Readonly<Record<string, unknown>>;
}

// =========================================
// Tenant Feature Flag
// =========================================

/**
 * Tenant-specific feature toggle.
 *
 * Features can be enabled/disabled per tenant for gradual rollouts
 * or tiered service offerings.
 */
export interface TenantFeature {
  /** Feature key (e.g., 'booking:advanced', 'analytics:export') */
  readonly key: string;

  /** Whether the feature is enabled for this tenant */
  readonly enabled: boolean;

  /** Optional feature configuration */
  readonly config?: Readonly<Record<string, unknown>>;
}

// =========================================
// Tenant Branding (Mobile)
// =========================================

/**
 * Tenant branding configuration for mobile rendering.
 *
 * Mobile-specific branding includes splash screen and app icon
 * overrides in addition to the shared web fields.
 */
export interface TenantBranding {
  /** Tenant logo URL */
  readonly logoUrl?: string;

  /** Primary brand color (hex format) */
  readonly primaryColor?: string;

  /** Secondary brand color (hex format) */
  readonly secondaryColor?: string;

  /** Splash screen background URL (mobile-specific) */
  readonly splashBgUrl?: string;

  /** Login page title text */
  readonly loginPageTitle?: string;

  /** Login page subtitle text */
  readonly loginPageSubtitle?: string;
}

// =========================================
// Tenant Capability Contract
// =========================================

/**
 * Tenant Capability Contract — Mobile Platform.
 *
 * Provides multi-tenancy context for mobile plugins. Plugins use this
 * capability to access tenant information, check feature flags, and
 * perform tenant-scoped operations.
 *
 * On mobile, unlike web:
 * - Token storage uses Keychain (iOS) / Keystore (Android)
 * - Tenant context is persisted via SecureStorageCapability
 * - No subdomain-based tenant resolution
 *
 * @example
 * ```typescript
 * const tenant = context.getCapability<TenantCapability>(TenantCapabilityType);
 * const tenantId = tenant.getCurrentTenantId();
 * const items = await http.get(`/bookings?tenantId=${tenantId}`);
 * ```
 *
 * @since 3.2.0
 */
export interface TenantCapability {
  /**
   * Get the current tenant ID.
   *
   * Returns the tenant ID from the authenticated context.
   * Returns null if no tenant context is available (e.g., before login).
   *
   * @returns the current tenant ID, or null if unavailable
   */
  getCurrentTenantId(): string | null;

  /**
   * Get the current tenant information.
   *
   * Returns full tenant details including name, status, and metadata.
   *
   * @returns the current tenant info, or null if unavailable
   */
  getCurrentTenant(): TenantInfo | null;

  /**
   * Get the list of tenants available to the current user.
   *
   * For users with multi-tenant access, returns all tenants they can
   * switch to. For single-tenant users, returns the current tenant only.
   *
   * @returns array of available tenants
   */
  getAvailableTenants(): readonly TenantInfo[];

  /**
   * Check if a specific feature is enabled for the current tenant.
   *
   * @param featureKey the feature key to check (e.g., 'booking:advanced')
   * @returns true if the feature is enabled, false otherwise
   */
  isFeatureEnabled(featureKey: string): boolean;

  /**
   * Switch to a different tenant.
   *
   * On mobile, this persists the new tenant context to secure storage
   * and re-registers push notification topics.
   *
   * @param tenantId the ID of the tenant to switch to
   * @returns Promise that resolves when the switch is complete
   * @throws Error if the user doesn't have access or tenant doesn't exist
   */
  switchTenant(tenantId: string): Promise<void>;

  /**
   * Subscribe to tenant context changes.
   *
   * Called when the tenant context changes (e.g., after tenant switch,
   * after login, or when tenant data is refreshed).
   *
   * @param listener callback invoked when tenant context changes
   * @returns Subscription with remove() method for cleanup
   */
  onTenantChange(listener: TenantChangeListener): Subscription;

  /**
   * Get the tenant branding configuration.
   *
   * Used for rendering tenant-specific logos, colors, and theme.
   *
   * @returns tenant branding, or null if not configured
   */
  getBranding(): TenantBranding | null;
}

// =========================================
// Tenant Change Event
// =========================================

/**
 * Tenant change event payload.
 */
export interface TenantChangeEvent {
  /** New tenant ID (null if tenant context cleared) */
  readonly tenantId: string | null;

  /** Previous tenant ID (null if no previous tenant) */
  readonly previousTenantId: string | null;

  /** New tenant info (null if tenant context cleared) */
  readonly tenant: TenantInfo | null;

  /** Change timestamp (epoch milliseconds) */
  readonly timestamp: number;
}

/**
 * Tenant change event listener.
 */
export type TenantChangeListener = (event: TenantChangeEvent) => void;

// =========================================
// Tenant Capability Config (for Host)
// =========================================

/**
 * Configuration interface for constructing TenantCapabilityImpl.
 *
 * The Host injects state accessor callbacks through this config,
 * and TenantCapabilityImpl wraps them into the formal contract.
 *
 * @since 3.2.0
 */
export interface TenantCapabilityConfig {
  /** Returns the current tenant ID, null if no tenant context */
  getCurrentTenantId: () => string | null;
  /** Returns the full tenant information object */
  getCurrentTenant: () => TenantInfo | null;
  /** Returns the list of tenants available to the current user */
  getAvailableTenants: () => readonly TenantInfo[];
  /** Checks whether a tenant-specific feature is enabled */
  isFeatureEnabled: (featureKey: string) => boolean;
  /** Switches the active tenant context */
  switchTenant: (tenantId: string) => Promise<void>;
  /** Returns the tenant branding configuration */
  getBranding: () => TenantBranding | null;
}
