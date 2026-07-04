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
 * @file TenantCapabilityImpl — Formal TenantCapability Contract Implementation
 * @description Bridges the existing TenantContext/TenantProvider infrastructure to
 * the formal TenantCapability contract defined in runtime-sdk-api-web. This class
 * is the standard Capability implementation layer, analogous to AuthCapabilityImpl
 * and I18nCapabilityImpl.
 *
 * @module @brix-sdk/platform-tenant-web/TenantCapabilityImpl
 * @version 3.1.0
 *
 * [Architecture Layer]
 * Layer 2C: Capability Implementation (platform-commons/client)
 * Wraps raw tenant state management and exposes it through the formal
 * TenantCapability contract, ensuring plugins only depend on the contract.
 *
 * [Design Pattern]
 * Config-delegation pattern: The Host passes state accessor functions and
 * callbacks via TenantCapabilityConfig, and this class wraps them into
 * a capability-compliant API. Identical pattern to AuthCapabilityImpl.
 *
 * [Lifecycle]
 * - Created during Host bootstrap (bootstrap.tsx)
 * - Registered into RuntimeContext via TenantCapabilityType
 * - Consumed by useTenant() hook in runtime-sdk-react
 * - Destroyed on unmount via destroy() method
 *
 * @since 3.1.0
 * @see TenantCapability - Contract definition in runtime-sdk-api-web
 * @see useTenant - React Hook in runtime-sdk-react
 */

import type {
  ActorTenantAccessContext,
  CurrentTenantAccessContext,
  TenantCapability,
  TenantInfo,
  TenantChangeEvent,
  TenantChangeListener,
  TenantCapabilityConfig,
} from '@brix-sdk/runtime-sdk-api-web';

// Re-export contract-layer type for backward compatibility
export type { TenantCapabilityConfig };

// ============================================================================
// Implementation Class
// ============================================================================

/**
 * TenantCapability Implementation.
 *
 * Wraps the Host-provided configuration callbacks into the formal
 * TenantCapability contract. Manages change listener subscriptions
 * and ensures proper cleanup on destroy.
 *
 * Thread-safety: This class is designed for single-threaded browser
 * environments. No synchronization is required.
 *
 * @since 3.1.0
 */
export class TenantCapabilityImpl implements TenantCapability {
  private readonly config: TenantCapabilityConfig;
  private readonly listeners: Set<TenantChangeListener> = new Set();
  private destroyed = false;

  /**
   * Creates a new TenantCapabilityImpl instance.
   *
   * @param config - Host-provided callbacks for tenant state access
   * @throws Error if config is null or undefined
   */
  constructor(config: TenantCapabilityConfig) {
    if (!config) {
      throw new Error(
        '[TenantCapabilityImpl] Configuration is required. ' +
        'Provide tenant state accessors via TenantCapabilityConfig.'
      );
    }
    this.config = config;
  }

  /**
   * Returns the current tenant ID from the authenticated context.
   *
   * The tenant ID is typically extracted from the JWT token by the Host
   * and made available through this capability. Plugins should always
   * use this method instead of parsing JWT tokens directly.
   *
   * @returns the current tenant ID, or null before authentication
   */
  getCurrentTenantId(): string | null {
    return this.config.getCurrentTenantId();
  }

  /**
   * Returns the full tenant information including name, status, and metadata.
   *
   * @returns the current tenant info, or null if not yet loaded
   */
  getCurrentTenant(): TenantInfo | null {
    return this.config.getCurrentTenant();
  }

  /**
   * Returns the current actor/subject access context.
   *
   * @returns the current access context, or null before context selection
   */
  getCurrentContext(): CurrentTenantAccessContext | null {
    return this.config.getCurrentContext?.() ?? null;
  }

  /**
   * Returns all tenants available to the current user.
   *
   * For users with multi-tenant access, returns the complete list.
   * For single-tenant users, returns an array containing only the
   * current tenant.
   *
   * @returns array of available tenants
   */
  getAvailableTenants(): readonly TenantInfo[] {
    return this.config.getAvailableTenants();
  }

  /**
   * Returns actor contexts that the current identity may switch to.
   *
   * Subject sessions must not expose switchable contexts.
   *
   * @returns switchable actor contexts
   */
  getAvailableContexts(): readonly ActorTenantAccessContext[] {
    return this.config.getAvailableContexts?.() ?? [];
  }

  /**
   * Checks if a specific feature is enabled for the current tenant.
   *
   * Feature flags are managed at the tenant level and can be used for
   * gradual rollouts, A/B testing, or tiered service offerings.
   *
   * @param featureKey - the feature key to check
   * @returns true if the feature is enabled for the current tenant
   */
  isFeatureEnabled(featureKey: string): boolean {
    return this.config.isFeatureEnabled(featureKey);
  }

  /**
   * Switches to a different tenant context.
   *
   * After switching, all tenant-scoped data should be refreshed.
   * Listeners registered via onTenantChange() will be notified.
   *
   * @param tenantId - the target tenant ID
   * @throws Error if the user lacks access or the tenant does not exist
   */
  async switchTenant(tenantId: string): Promise<void> {
    if (!tenantId) {
      throw new Error('[TenantCapabilityImpl] Tenant ID is required for switching.');
    }

    const previousTenantId = this.getCurrentTenantId();
    await this.config.switchTenant(tenantId);

    // Notify all listeners of the tenant change
    const event: TenantChangeEvent = {
      tenantId,
      previousTenantId,
      tenant: this.getCurrentTenant(),
      context: this.getCurrentContext(),
      timestamp: Date.now(),
    };
    this.notifyListeners(event);
  }

  /**
   * Switches to a different actor context by stable context id.
   *
   * @param contextId - target actor context id
   * @throws Error if context switching is not provided by the Host
   */
  async switchContext(contextId: string): Promise<void> {
    if (!contextId) {
      throw new Error('[TenantCapabilityImpl] Context ID is required for switching.');
    }
    if (!this.config.switchContext) {
      throw new Error(
        '[TenantCapabilityImpl] switchContext is not configured. ' +
        'Provide a Phase 3 context-switch callback during bootstrap.',
      );
    }

    const previousTenantId = this.getCurrentTenantId();
    await this.config.switchContext(contextId);

    const event: TenantChangeEvent = {
      tenantId: this.getCurrentTenantId(),
      previousTenantId,
      tenant: this.getCurrentTenant(),
      context: this.getCurrentContext(),
      timestamp: Date.now(),
    };
    this.notifyListeners(event);
  }

  /**
   * Subscribes to tenant context changes.
   *
   * Listeners are invoked when:
   * - The tenant is switched via switchTenant()
   * - The tenant context is cleared (logout)
   * - The tenant data is refreshed
   *
   * @param listener - callback invoked on tenant changes
   * @returns unsubscribe function — call to remove the listener
   */
  onTenantChange(listener: TenantChangeListener): () => void {
    if (this.destroyed) {
      return () => {};
    }

    this.listeners.add(listener);

    return () => {
      this.listeners.delete(listener);
    };
  }

  /**
   * Cleans up all subscriptions and prevents further notifications.
   *
   * Called during Host unmount to prevent memory leaks.
   * After destroy(), onTenantChange() returns no-op unsubscribe functions.
   */
  destroy(): void {
    this.destroyed = true;
    this.listeners.clear();
  }

  /**
   * Notifies all registered listeners of a tenant change event.
   *
   * @param event - the change event to broadcast
   * @internal
   */
  private notifyListeners(event: TenantChangeEvent): void {
    for (const listener of this.listeners) {
      try {
        listener(event);
      } catch (error) {
        // Listener failures must be visible; one broken subscriber must not
        // prevent the rest of the runtime from receiving the context change.
        // eslint-disable-next-line no-console
        console.error('[TenantCapabilityImpl] tenant change listener failed:', error);
      }
    }
  }
}
