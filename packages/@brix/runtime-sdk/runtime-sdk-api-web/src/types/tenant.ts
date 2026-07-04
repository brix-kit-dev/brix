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
 * @file Tenant Capability Type Definitions
 * @description Defines the TenantCapability contract for multi-tenancy support.
 * Aligned with the Java TenantCapability interface in runtime-sdk-api.
 * @module @brix-sdk/runtime-sdk-api-web/types/tenant
 * @version 3.1.0
 *
 * [v3.1.0 Added — Phase 1.8]
 * Formal TenantCapability contract definition at the SDK API layer.
 * Previously, tenant support existed only in platform-tenant-web implementation
 * without a formal contract in runtime-sdk-api-web.
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
 */

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
// Tenant Access Context
// =========================================

/**
 * Access-context role track.
 *
 * Actor is the B-side organizational member track. Subject is the C-side
 * customer/guest track. The two tracks are intentionally separated so UI code
 * cannot accidentally expose actor-only context switching to subject sessions.
 */
export type TenantAccessRole = 'actor' | 'subject';

/**
 * Tenant-scoped role type.
 *
 * Actor contexts commonly use OWNER / ADMIN / MEMBER. Subject contexts
 * commonly use CUSTOMER / GUEST. The string extension keeps the web contract
 * forward-compatible with new role codes added by backend policy.
 */
export type TenantContextRoleType =
  | 'OWNER'
  | 'ADMIN'
  | 'MEMBER'
  | 'CUSTOMER'
  | 'GUEST'
  | (string & {});

/**
 * Stable tenant access context bound into a signed token.
 *
 * `contextId` is the only frontend switching identifier. UI code must not use
 * `tenantId` as an authorization or context-switching parameter.
 */
export interface TenantAccessContext {
  /** Actor or subject track. */
  readonly role: TenantAccessRole;
  /** Stable backend context id (`cid` claim). */
  readonly contextId: string;
  /** Tenant id of this access context. */
  readonly tenantId: string;
  /** Tenant summary for display only. */
  readonly tenant: TenantInfo;
  /** Tenant-scoped role type, e.g. OWNER / ADMIN / MEMBER / CUSTOMER / GUEST. */
  readonly roleType: TenantContextRoleType;
  /** Member id for actor, principal id for subject. */
  readonly refId: string;
  /** Global identity/user id. */
  readonly userId: string;
  /** Display name resolved for this tenant context. */
  readonly displayName: string;
  /** Last time this context was used, if available. */
  readonly lastAccessAt?: string;
  /** Additional non-authoritative display metadata. */
  readonly metadata?: Readonly<Record<string, unknown>>;
}

/**
 * Actor access context. Only actor contexts are switchable from the frontend.
 */
export interface ActorTenantAccessContext extends TenantAccessContext {
  readonly role: 'actor';
}

/**
 * Subject access context. Subject sessions are single-context and not
 * switchable from the frontend.
 */
export interface SubjectTenantAccessContext extends TenantAccessContext {
  readonly role: 'subject';
}

export type CurrentTenantAccessContext =
  | ActorTenantAccessContext
  | SubjectTenantAccessContext;

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
// Tenant Capability Contract
// =========================================

/**
 * Tenant Capability Contract.
 *
 * Provides multi-tenancy context for plugins. Plugins use this capability
 * to access tenant information, check feature flags, and perform
 * tenant-scoped operations.
 *
 * <h3>Usage via Hook</h3>
 * ```tsx
 * import { useTenant } from '@brix/runtime-sdk-react';
 *
 * function MyComponent() {
 *   const { tenantId, tenant, isFeatureEnabled } = useTenant();
 *
 *   if (isFeatureEnabled('booking:advanced')) {
 *     return <AdvancedBooking tenantId={tenantId} />;
 *   }
 *   return <BasicBooking tenantId={tenantId} />;
 * }
 * ```
 *
 * <h3>Usage via Capability API</h3>
 * ```typescript
 * const tenantCap = context.getCapability<TenantCapability>(TenantCapabilityType);
 * const tenantId = tenantCap.getCurrentTenantId();
 * const items = await http.get(`/bookings?tenantId=${tenantId}`);
 * ```
 *
 * @since 3.1.0
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
   * May trigger an async fetch if tenant data is not yet cached.
   *
   * @returns the current tenant info, or null if unavailable
   */
  getCurrentTenant(): TenantInfo | null;

  /**
   * Get the current actor/subject access context.
   *
   * Returns null before a tenant-scoped token is selected. Implementations that
   * still expose only the legacy tenant API may omit this method during a
   * compatibility window; React hooks derive a display-only actor context from
   * the legacy tenant snapshot in that case.
   */
  getCurrentContext?(): CurrentTenantAccessContext | null;

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
   * Get switchable actor contexts.
   *
   * Subject sessions must return an empty array. The UI must never expose these
   * values for subject sessions.
   */
  getAvailableContexts?(): readonly ActorTenantAccessContext[];

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
   * This operation requires the user to have access to the target tenant.
   * After switching, all tenant-scoped data will be refreshed.
   *
   * @param tenantId the ID of the tenant to switch to
   * @returns Promise that resolves when the switch is complete
   * @throws Error if the user doesn't have access or tenant doesn't exist
   */
  switchTenant(tenantId: string): Promise<void>;

  /**
   * Switch to another actor context by stable context id.
   *
   * The backend must re-sign tokens and invalidate the previous token family as
   * needed. The frontend must clear tenant-scoped caches after this completes.
   */
  switchContext?(contextId: string): Promise<void>;

  /**
   * Subscribe to tenant context changes.
   *
   * Called when the tenant context changes (e.g., after tenant switch,
   * after login, or when tenant data is refreshed).
   *
   * @param listener callback invoked when tenant context changes
   * @returns unsubscribe function
   */
  onTenantChange(listener: TenantChangeListener): () => void;
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

  /** New actor/subject context, null when cleared. */
  readonly context?: CurrentTenantAccessContext | null;

  /** Change timestamp (epoch milliseconds) */
  readonly timestamp: number;
}

/**
 * Tenant change event listener.
 */
export type TenantChangeListener = (event: TenantChangeEvent) => void;
