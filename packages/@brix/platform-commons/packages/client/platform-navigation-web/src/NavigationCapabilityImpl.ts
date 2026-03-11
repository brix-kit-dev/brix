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
 * @file Navigation Capability Implementation
 * @description Implements NavigationCapability interface
 * @module @brix/platform-navigation-web/NavigationCapabilityImpl
 * @version 3.0.0
 * 
 * [Architectural Notes]
 * NavigationCapabilityImpl is the implementation of the NavigationCapability interface.
 * It serves as a bridge connecting plugin navigation requests and the underlying router service.
 * 
 * [Core Responsibilities]
 * 1. Convert PageId to URL
 * 2. Execute governance policy checks
 * 3. Call RouterService to perform actual navigation
 * 4. Provide page change event subscription
 * 
 * [Architectural Relationships]
 * ```text
 * Plugin Code
 *   ↓ calls requestNavigate('booking:detail', { id: '123' })
 * NavigationCapabilityImpl (this class)
 *   ↓ 1. PageRegistry.resolve() find page
 *   ↓ 2. GovernancePolicy.canNavigate() check permissions
 *   ↓ 3. PageRegistry.buildUrl() build URL
 *   ↓ 4. RouterService.navigate() execute navigation
 * react-router-dom
 * ```
 * 
 * [Key Design]
 * Plugins only call NavigationCapability, unaware of RouterService existence!
 * This achieves true platform independence.
 */

import type { NavigationCapability, NavigateOptions, NavigateResult, WebNavigateOptions, PageChangeEvent, PageChangeHandler, Unsubscribe } from '@brix/runtime-sdk-api-web';
import type { RouterService } from '@brix/platform-router-web';
import type { PageRegistry } from './PageRegistry';
import type { GovernancePolicy } from './types';

/**
 * Navigation capability implementation configuration
 */
export interface NavigationCapabilityConfig {
  /**
   * Router service instance
   */
  routerService: RouterService;
  
  /**
   * Page registry instance
   */
  pageRegistry: PageRegistry;
  
  /**
   * Governance policy instance
   */
  governancePolicy: GovernancePolicy;
  
  /**
   * Current plugin ID (for governance policy checks)
   */
  pluginId: string;
}

/**
 * Navigation Capability Implementation
 * 
 * Implements NavigationCapability interface, providing PageId-based navigation capability.
 * 
 * [Usage Example]
 * ```typescript
 * // Created during Host initialization
 * const navigationCapability = new NavigationCapabilityImpl({
 *   routerService: reactRouterAdapter,
 *   pageRegistry: pageRegistry,
 *   governancePolicy: governancePolicy,
 *   pluginId: 'booking',
 * });
 * 
 * // Inject into plugin capability context
 * capabilityRegistry.register(NavigationCapability, navigationCapability);
 * ```
 */
export class NavigationCapabilityImpl implements NavigationCapability {
  /**
   * Router service
   */
  private routerService: RouterService;
  
  /**
   * Page registry
   */
  private pageRegistry: PageRegistry;
  
  /**
   * Governance policy
   */
  private governancePolicy: GovernancePolicy;
  
  /**
   * Current plugin ID
   */
  private pluginId: string;
  
  /**
   * Page change listener set
   */
  private pageChangeListeners: Set<PageChangeHandler> = new Set();
  
  /**
   * RouterService URL change subscription unsubscribe function
   */
  private urlChangeUnsubscribe: Unsubscribe | null = null;
  
  /**
   * Previous page ID (for calculating page changes)
   */
  private lastPageId: string = '';
  
  /**
   * Constructor
   * 
   * @param config - Configuration object
   */
  constructor(config: NavigationCapabilityConfig) {
    this.routerService = config.routerService;
    this.pageRegistry = config.pageRegistry;
    this.governancePolicy = config.governancePolicy;
    this.pluginId = config.pluginId;
    
    // Subscribe to URL changes, convert to page change events
    this.setupUrlChangeListener();
    
    // Initialize current page ID
    this.lastPageId = this.getCurrentPageId();
  }

  // =========================================
  // Basic Navigation Methods (Imperative)
  // =========================================

  /**
   * Navigate to specified path
   * 
   * Imperative navigation, executed directly without governance checks.
   * For controlled navigation, use requestNavigate.
   * 
   * @param path - Target path
   * @param options - Navigation options
   */
  navigate(path: string, options?: NavigateOptions): void {
    this.routerService.navigate(path, {
      replace: options?.replace ?? false,
    });
  }

  /**
   * Go back to previous page
   */
  goBack(): void {
    this.routerService.goBack();
  }

  /**
   * Get current path
   * 
   * @returns Current URL path
   */
  getCurrentPath(): string {
    return this.routerService.getCurrentPath();
  }

  // =========================================
  // Advanced Navigation Methods (Request-Based)
  // =========================================
  
  /**
   * Request navigation to specified page
   * 
   * [Important] This is a "request" not a "command", Host can deny navigation requests.
   * 
   * @param pageId - Target page ID
   * @param params - Page parameters
   * @param options - Navigation options
   * @returns Navigation result
   */
  async requestNavigate(
    pageId: string, 
    params?: Record<string, unknown>, 
    options?: WebNavigateOptions
  ): Promise<NavigateResult> {
    try {
      // 1. Check governance policy
      if (!this.governancePolicy.canNavigate(pageId, this.pluginId)) {
        const denialReason = this.governancePolicy.getDenialReason(pageId, this.pluginId);
        // Map internal denial reason to interface-defined reason type
        const reason = this.mapDenialReason(denialReason);
        return {
          success: false,
          reason,
          message: denialReason ?? `Navigation to ${pageId} was denied`,
        };
      }
      
      // 2. Check if page exists
      const pageInfo = this.pageRegistry.resolve(pageId);
      if (!pageInfo) {
        return {
          success: false,
          reason: 'page_not_found',
          message: `Unknown page ID: ${pageId}`,
        };
      }
      
      // 3. Build URL
      let url: string;
      try {
        url = this.pageRegistry.buildUrl(pageId, params as Record<string, string | number> | undefined);
      } catch (error) {
        return {
          success: false,
          reason: 'host_rejected',
          message: error instanceof Error ? error.message : 'Failed to build URL',
        };
      }
      
      // 4. Execute navigation
      this.routerService.navigate(url, {
        replace: options?.replace ?? false,
      });
      
      return {
        success: true,
      };
    } catch (error) {
      return {
        success: false,
        reason: 'host_rejected',
        message: error instanceof Error ? error.message : 'Navigation failed',
      };
    }
  }
  
  /**
   * Maps internal denial reason to interface-defined reason type.
   * 
   * @param denialReason - Internal denial reason string
   * @returns Interface-defined reason type
   */
  private mapDenialReason(denialReason: string | undefined): 'permission_denied' | 'feature_disabled' | 'page_not_found' | 'host_rejected' | 'navigation_blocked' {
    if (!denialReason) {
      return 'host_rejected';
    }
    
    const lowerReason = denialReason.toLowerCase();
    if (lowerReason.includes('permission') || lowerReason.includes('权限')) {
      return 'permission_denied';
    }
    if (lowerReason.includes('feature') || lowerReason.includes('功能') || lowerReason.includes('禁用')) {
      return 'feature_disabled';
    }
    if (lowerReason.includes('not found') || lowerReason.includes('不存在') || lowerReason.includes('未找到')) {
      return 'page_not_found';
    }
    if (lowerReason.includes('blocked') || lowerReason.includes('阻止')) {
      return 'navigation_blocked';
    }
    
    return 'host_rejected';
  }
  
  /**
   * Checks if navigation to the specified page is allowed.
   * 
   * @param pageId - Target page ID
   * @returns Whether navigation is allowed
   */
  canNavigate(pageId: string): boolean {
    // Check if page exists
    if (!this.pageRegistry.has(pageId)) {
      return false;
    }
    
    // Check governance policy
    return this.governancePolicy.canNavigate(pageId, this.pluginId);
  }
  
  /**
   * Gets the current page ID.
   * 
   * @returns Current page ID, or empty string if unidentifiable
   */
  getCurrentPageId(): string {
    const currentUrl = this.routerService.getCurrentUrl();
    const pageInfo = this.pageRegistry.resolveByUrl(currentUrl);
    
    return pageInfo?.pageId ?? '';
  }
  
  /**
   * Gets the current page parameters.
   * 
   * Returns parameters passed to the current page. These parameters come from 
   * the `params` passed during navigation.
   * 
   * @typeParam T - Parameter type
   * @returns Page parameters object
   * 
   * @example
   * ```typescript
   * interface BookingDetailParams {
   *   id: string;
   *   source?: string;
   * }
   * 
   * const params = navigation.getPageParams<BookingDetailParams>();
   * console.log(params.id); // '123'
   * ```
   */
  getPageParams<T extends Record<string, unknown> = Record<string, unknown>>(): T {
    const queryParams = this.routerService.getQueryParams();
    return queryParams as unknown as T;
  }
  
  /**
   * Requests navigation back to the previous page.
   * 
   * @returns Navigation result
   */
  async requestGoBack(): Promise<NavigateResult> {
    try {
      this.routerService.goBack();
      return {
        success: true,
      };
    } catch (error) {
      return {
        success: false,
        reason: 'host_rejected',
        message: error instanceof Error ? error.message : 'Go back failed',
      };
    }
  }
  
  /**
   * Subscribes to page change events.
   * 
   * @param handler - Page change handler function
   * @returns Unsubscribe function
   */
  onPageChange(handler: PageChangeHandler): Unsubscribe {
    this.pageChangeListeners.add(handler);
    
    return () => {
      this.pageChangeListeners.delete(handler);
    };
  }
  
  /**
   * Sets up URL change listener.
   */
  private setupUrlChangeListener(): void {
    this.urlChangeUnsubscribe = this.routerService.onUrlChange((url) => {
      this.handleUrlChange(url);
    });
  }
  
  /**
   * Handles URL changes.
   * 
   * @param url - The new URL
   */
  private handleUrlChange(url: string): void {
    const newPageInfo = this.pageRegistry.resolveByUrl(url);
    const newPageId = newPageInfo?.pageId ?? '';
    
    // Trigger event when page ID changes
    if (newPageId !== this.lastPageId) {
      const params = this.routerService.getQueryParams() as Record<string, unknown>;
      const event: PageChangeEvent = {
        pageId: newPageId,
        params,
        fromPageId: this.lastPageId || undefined,
        navigationType: 'push', // Default to 'push', RouterService can provide more precise type
      };
      
      this.lastPageId = newPageId;
      this.notifyPageChange(event);
    }
  }
  
  /**
   * Notifies page change listeners.
   * 
   * @param event - Page change event
   */
  private notifyPageChange(event: PageChangeEvent): void {
    this.pageChangeListeners.forEach(handler => {
      try {
        handler(event);
      } catch (error) {
        console.error('[NavigationCapability] Page change handler execution error:', error);
      }
    });
  }
  
  /**
   * Destroys the capability instance.
   */
  destroy(): void {
    if (this.urlChangeUnsubscribe) {
      this.urlChangeUnsubscribe();
      this.urlChangeUnsubscribe = null;
    }
    
    this.pageChangeListeners.clear();
  }
}
