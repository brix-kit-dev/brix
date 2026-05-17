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
 * @file MobileTenantCapabilityImpl — Mobile TenantCapability Contract Implementation
 * @description Bridges the MobileTenantProvider infrastructure to the formal
 * TenantCapability contract defined in runtime-sdk-api-mobile. Analogous to
 * TenantCapabilityImpl in platform-tenant-web.
 *
 * @module @brix-sdk/platform-tenant-mobile/MobileTenantCapabilityImpl
 * @version 3.2.0
 *
 * [Architecture Layer]
 * Layer 2C: Capability Implementation (platform-commons/client)
 * Wraps raw tenant state management and exposes it through the formal
 * TenantCapability contract, ensuring plugins only depend on the contract.
 *
 * [Design Pattern]
 * Config-delegation pattern: The Host passes state accessor functions and
 * callbacks via TenantCapabilityConfig, and this class wraps them into
 * a capability-compliant API. Identical to the web TenantCapabilityImpl.
 *
 * [Lifecycle]
 * - Created during mobile Host bootstrap
 * - Registered into RuntimeContext via TenantCapabilityType
 * - Consumed by useMobileTenant() hook
 * - Destroyed on unmount via destroy() method
 *
 * @since 3.2.0
 */

import type {
  TenantCapability,
  TenantCapabilityConfig,
  TenantInfo,
  TenantBranding,
  TenantChangeEvent,
  TenantChangeListener,
  Subscription,
} from '@brix-sdk/runtime-sdk-api-mobile';

// Re-export contract-layer type for backward compatibility
export type { TenantCapabilityConfig };

/**
 * MobileTenantCapabilityImpl — TenantCapability implementation for mobile.
 *
 * Wraps the Host-provided configuration callbacks into the formal
 * TenantCapability contract. Manages change listener subscriptions
 * and ensures proper cleanup on destroy.
 *
 * @since 3.2.0
 */
export class MobileTenantCapabilityImpl implements TenantCapability {
  private readonly config: TenantCapabilityConfig;
  private readonly listeners: Set<TenantChangeListener> = new Set();
  private destroyed = false;

  /**
   * Creates a new MobileTenantCapabilityImpl instance.
   *
   * @param config Host-provided callbacks for tenant state access
   * @throws Error if config is null or undefined
   */
  constructor(config: TenantCapabilityConfig) {
    if (!config) {
      throw new Error(
        '[MobileTenantCapabilityImpl] Configuration is required. ' +
        'Provide tenant state accessors via TenantCapabilityConfig.'
      );
    }
    this.config = config;
  }

  /**
   * Returns the current tenant ID from the authenticated context.
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
   * Returns all tenants available to the current user.
   *
   * @returns array of available tenants
   */
  getAvailableTenants(): readonly TenantInfo[] {
    return this.config.getAvailableTenants();
  }

  /**
   * Checks if a specific feature is enabled for the current tenant.
   *
   * @param featureKey the feature key to check
   * @returns true if the feature is enabled for the current tenant
   */
  isFeatureEnabled(featureKey: string): boolean {
    return this.config.isFeatureEnabled(featureKey);
  }

  /**
   * Switches to a different tenant context.
   *
   * On mobile, this also triggers:
   * - Secure storage update (last tenant ID)
   * - Push notification topic re-subscription
   * - Branding data refresh
   *
   * @param tenantId the target tenant ID
   * @throws Error if the user lacks access or the tenant does not exist
   */
  async switchTenant(tenantId: string): Promise<void> {
    if (!tenantId) {
      throw new Error('[MobileTenantCapabilityImpl] Tenant ID is required for switching.');
    }

    const previousTenantId = this.getCurrentTenantId();
    await this.config.switchTenant(tenantId);

    // Notify all listeners
    const event: TenantChangeEvent = {
      tenantId,
      previousTenantId,
      tenant: this.getCurrentTenant(),
      timestamp: Date.now(),
    };
    this.notifyListeners(event);
  }

  /**
   * Subscribes to tenant context changes.
   *
   * @param listener callback invoked on tenant changes
   * @returns Subscription with remove() method
   */
  onTenantChange(listener: TenantChangeListener): Subscription {
    if (this.destroyed) {
      return { unsubscribe: () => {} };
    }

    this.listeners.add(listener);

    return {
      unsubscribe: () => {
        this.listeners.delete(listener);
      },
    };
  }

  /**
   * Get the tenant branding configuration.
   *
   * @returns tenant branding, or null if not configured
   */
  getBranding(): TenantBranding | null {
    return this.config.getBranding();
  }

  /**
   * Cleans up all subscriptions and prevents further notifications.
   *
   * Called during Host unmount to prevent memory leaks.
   */
  destroy(): void {
    this.destroyed = true;
    this.listeners.clear();
  }

  /**
   * Notify all registered listeners of a tenant change.
   *
   * @param event the tenant change event
   */
  private notifyListeners(event: TenantChangeEvent): void {
    for (const listener of this.listeners) {
      try {
        listener(event);
      } catch {
        // Listener errors should not break the notification chain
      }
    }
  }
}
