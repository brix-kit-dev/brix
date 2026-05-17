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
 * @file Layout Capability Implementation
 * @description Implements LayoutCapability interface
 * @module @brix-sdk/platform-frame-web/LayoutCapabilityImpl
 * @version 3.0.0
 * 
 * [Architecture Notes]
 * LayoutCapabilityImpl is the implementation of LayoutCapability interface.
 * It handles layout requests from plugins and applies governance policies.
 * 
 * [Layout Management Principles]
 * 1. Layout is fully controlled by Host
 * 2. Plugins can only "request" layout changes, Host decides whether to allow
 * 3. All requests go through governance policy checks
 * 
 * [Architectural Constraints]
 * - Plugins are prohibited from directly manipulating document.body
 * - Plugins are prohibited from creating global Portals on body
 * - Plugins are prohibited from modifying global CSS (e.g., overflow)
 * - Plugins can only request layout changes through LayoutCapability
 */

import type { 
  LayoutCapability, 
  LayoutState, 
  LayoutRequestResult,
  LayoutChangeHandler,
  Unsubscribe,
} from '@brix-sdk/runtime-sdk-api-web';
import type { LayoutCapabilityConfig } from '@brix-sdk/runtime-sdk-api-web';
import { LayoutStore } from './LayoutStore';
import { GovernancePolicyHandler } from './GovernancePolicy';
import type { LayoutConfig, LayoutGovernancePolicy, LayoutChangeRequest } from './layout-types';

// Re-export contract-layer type for backward compatibility
export type { LayoutCapabilityConfig };

/**
 * Layout Capability Implementation
 * 
 * Implements LayoutCapability interface, provides layout control capabilities.
 * 
 * [Usage Example]
 * ```typescript
 * // Create during Host initialization
 * const layoutStore = new LayoutStore({ layoutMode: 'console' });
 * const governance = new GovernancePolicyHandler({ allowFullscreen: true });
 * 
 * // Create capability instance for plugin
 * const layoutCapability = new LayoutCapabilityImpl({
 *   pluginId: 'booking-plugin',
 *   layoutStore,
 *   governancePolicy: governance.getPolicy(),
 * });
 * 
 * // Plugin usage
 * const success = await layoutCapability.requestFullscreen();
 * ```
 */
export class LayoutCapabilityImpl implements LayoutCapability {
  /**
   * Plugin ID
   */
  private pluginId: string;
  
  /**
   * Layout store
   */
  private layoutStore: LayoutStore;
  
  /**
   * Governance policy handler
   */
  private governanceHandler: GovernancePolicyHandler;
  
  /**
   * Set of subscription cancellation functions
   */
  private subscriptions: Set<Unsubscribe> = new Set();
  
  /**
   * Whether this instance owns the layout store
   */
  private ownsLayoutStore: boolean;
  
  /**
   * Constructor
   * 
   * @param config - Configuration object
   */
  constructor(config: LayoutCapabilityConfig) {
    this.pluginId = config.pluginId;
    
    // Use shared layout store or create new one
    if (config.layoutStore) {
      this.layoutStore = config.layoutStore;
      this.ownsLayoutStore = false;
    } else {
      this.layoutStore = new LayoutStore(config);
      this.ownsLayoutStore = true;
    }
    
    // Create governance policy handler
    this.governanceHandler = new GovernancePolicyHandler(config.governancePolicy);
  }
  
  /**
   * Request fullscreen mode
   * 
   * @returns Whether fullscreen was successfully entered
   */
  async requestFullscreen(): Promise<boolean> {
    const result = await this.requestLayoutChange({ fullscreen: true });
    return result.success;
  }
  
  /**
   * Request exit fullscreen mode
   * 
   * @returns Whether fullscreen was successfully exited
   */
  async requestExitFullscreen(): Promise<boolean> {
    const result = await this.requestLayoutChange({ fullscreen: false });
    return result.success;
  }
  
  /**
   * Request hide sidebar
   * 
   * @returns Whether hide was successful
   */
  async requestHideSidebar(): Promise<boolean> {
    const result = await this.requestLayoutChange({ sidebarVisible: false });
    return result.success;
  }
  
  /**
   * Request show sidebar
   * 
   * @returns Whether show was successful
   */
  async requestShowSidebar(): Promise<boolean> {
    const result = await this.requestLayoutChange({ sidebarVisible: true });
    return result.success;
  }
  
  /**
   * Request collapse sidebar
   * 
   * @returns Whether collapse was successful
   */
  async requestCollapseSidebar(): Promise<boolean> {
    const result = await this.requestLayoutChange({ sidebarCollapsed: true });
    return result.success;
  }
  
  /**
   * Request expand sidebar
   * 
   * @returns Whether expand was successful
   */
  async requestExpandSidebar(): Promise<boolean> {
    const result = await this.requestLayoutChange({ sidebarCollapsed: false });
    return result.success;
  }
  
  /**
   * Request hide header
   * 
   * @returns Whether hide was successful
   */
  async requestHideHeader(): Promise<boolean> {
    const result = await this.requestLayoutChange({ headerVisible: false });
    return result.success;
  }
  
  /**
   * Request show header
   * 
   * @returns Whether show was successful
   */
  async requestShowHeader(): Promise<boolean> {
    const result = await this.requestLayoutChange({ headerVisible: true });
    return result.success;
  }
  
  /**
   * Get current layout state
   * 
   * @returns Current layout state (read-only)
   */
  getLayoutState(): LayoutState {
    return this.layoutStore.getState();
  }
  
  /**
   * Subscribe to layout state changes
   * 
   * @param handler - Layout change handler
   * @returns Unsubscribe function
   */
  onLayoutChange(handler: LayoutChangeHandler): Unsubscribe {
    const unsubscribe = this.layoutStore.subscribe(handler);
    
    // Record subscription for cleanup
    this.subscriptions.add(unsubscribe);
    
    return () => {
      unsubscribe();
      this.subscriptions.delete(unsubscribe);
    };
  }
  
  /**
   * Request layout change
   * 
   * @param options - Layout change options
   * @returns Request result
   */
  async requestLayoutChange(options: {
    fullscreen?: boolean;
    sidebarVisible?: boolean;
    sidebarCollapsed?: boolean;
    headerVisible?: boolean;
  }): Promise<LayoutRequestResult> {
    // Build change request
    const changes: Partial<LayoutState> = {};
    
    if (options.fullscreen !== undefined) {
      changes.isFullscreen = options.fullscreen;
    }
    if (options.sidebarVisible !== undefined) {
      changes.isSidebarVisible = options.sidebarVisible;
    }
    if (options.sidebarCollapsed !== undefined) {
      changes.isSidebarCollapsed = options.sidebarCollapsed;
    }
    if (options.headerVisible !== undefined) {
      changes.isHeaderVisible = options.headerVisible;
    }
    
    // Create change request
    const request: LayoutChangeRequest = {
      type: 'batch',
      pluginId: this.pluginId,
      changes,
      timestamp: Date.now(),
    };
    
    // Check governance policy
    const checkResult = this.governanceHandler.checkRequest(request);
    
    if (!checkResult.success) {
      console.warn(
        `[LayoutCapability] Layout request from plugin ${this.pluginId} was rejected:`,
        checkResult.message
      );
      return checkResult;
    }
    
    // Check if already in target state
    const currentState = this.layoutStore.getState();
    const isAlreadyInState = Object.entries(changes).every(
      ([key, value]) => currentState[key as keyof LayoutState] === value
    );
    
    if (isAlreadyInState) {
      return {
        success: true,
        reason: 'already_in_state',
        message: 'Already in target state',
      };
    }
    
    // Apply changes
    this.layoutStore.update(changes, 'plugin', this.pluginId);
    
    // Handle browser fullscreen API
    if (options.fullscreen !== undefined) {
      try {
        await this.handleBrowserFullscreen(options.fullscreen);
      } catch (error) {
        console.warn('[LayoutCapability] Browser fullscreen API call failed:', error);
        // Does not affect layout state, browser fullscreen just may not work
      }
    }
    
    return { success: true };
  }
  
  /**
   * Handle browser fullscreen API
   * 
   * @param fullscreen - Whether to enter fullscreen
   */
  private async handleBrowserFullscreen(fullscreen: boolean): Promise<void> {
    if (typeof document === 'undefined') {
      return;
    }
    
    if (fullscreen) {
      // Enter fullscreen
      const elem = document.documentElement;
      if (elem.requestFullscreen) {
        await elem.requestFullscreen();
      } else if ((elem as any).webkitRequestFullscreen) {
        await (elem as any).webkitRequestFullscreen();
      } else if ((elem as any).msRequestFullscreen) {
        await (elem as any).msRequestFullscreen();
      }
    } else {
      // Exit fullscreen
      if (document.exitFullscreen) {
        await document.exitFullscreen();
      } else if ((document as any).webkitExitFullscreen) {
        await (document as any).webkitExitFullscreen();
      } else if ((document as any).msExitFullscreen) {
        await (document as any).msExitFullscreen();
      }
    }
  }
  
  /**
   * Get layout store (for Host use)
   * 
   * @returns Layout store instance
   */
  getLayoutStore(): LayoutStore {
    return this.layoutStore;
  }
  
  /**
   * Update governance policy (for Host use)
   * 
   * @param policy - New layout policy
   */
  updateGovernancePolicy(policy: Partial<LayoutGovernancePolicy>): void {
    this.governanceHandler.updatePolicy(policy);
  }
  
  /**
   * Destroy capability instance
   */
  destroy(): void {
    // Cancel all subscriptions
    this.subscriptions.forEach(unsubscribe => unsubscribe());
    this.subscriptions.clear();
    
    // Destroy layout store if we own it
    if (this.ownsLayoutStore) {
      this.layoutStore.destroy();
    }
  }
}
