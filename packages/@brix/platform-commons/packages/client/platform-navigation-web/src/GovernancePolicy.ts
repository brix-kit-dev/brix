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
 * @file Navigation Governance Policy
 * @description Implements permission checking and governance policies for navigation
 * @module @brix/platform-navigation-web/GovernancePolicy
 * @version 3.0.0
 * 
 * [Architectural Notes]
 * GovernancePolicyImpl implements governance policies for navigation.
 * Host can control which plugins can navigate to which pages through configuration.
 * 
 * [Governance Dimensions]
 * 1. Permission Check: Whether the user has permission to access the target page
 * 2. Plugin Isolation: Whether cross-plugin navigation is allowed
 * 3. Feature Flag: Whether the page is disabled
 * 4. Tenant Configuration: Page visibility at tenant level
 */

import type { GovernancePolicy, PageInfo } from './types';
import type { PageRegistry } from './PageRegistry';

/**
 * Governance configuration
 */
export interface GovernanceConfig {
  /**
   * Whether to allow cross-plugin navigation
   * 
   * @default true
   */
  allowCrossPluginNavigation?: boolean;
  
  /**
   * List of disabled pages
   */
  disabledPages?: string[];
  
  /**
   * User permission list
   */
  userPermissions?: string[];
  
  /**
   * Custom permission checker
   */
  permissionChecker?: (pageId: string, permissions: string[]) => boolean;
}

/**
 * Navigation governance policy implementation
 * 
 * Implements governance policy checks for navigation requests.
 * 
 * [Usage Example]
 * ```typescript
 * const policy = new GovernancePolicyImpl(pageRegistry, {
 *   userPermissions: ['booking:read', 'booking:write'],
 *   disabledPages: ['admin:danger-page'],
 * });
 * 
 * if (policy.canNavigate('booking:list', 'booking')) {
 *   // Navigation allowed
 * } else {
 *   const reason = policy.getDenialReason('booking:list', 'booking');
 *   console.log('Navigation denied', reason);
 * }
 * ```
 */
export class GovernancePolicyImpl implements GovernancePolicy {
  /**
   * Page registry reference
   */
  private pageRegistry: PageRegistry;
  
  /**
   * Governance configuration
   */
  private config: GovernanceConfig;
  
  /**
   * Cached denial reasons
   */
  private denialReasons: Map<string, string> = new Map();
  
  /**
   * Constructor
   * 
   * @param pageRegistry - Page registry
   * @param config - Governance configuration
   */
  constructor(pageRegistry: PageRegistry, config: GovernanceConfig = {}) {
    this.pageRegistry = pageRegistry;
    this.config = {
      allowCrossPluginNavigation: true,
      disabledPages: [],
      userPermissions: [],
      ...config,
    };
  }
  
  /**
   * Update configuration
   * 
   * @param config - Partial configuration
   */
  updateConfig(config: Partial<GovernanceConfig>): void {
    this.config = { ...this.config, ...config };
    this.denialReasons.clear();
  }
  
  /**
   * Set user permissions
   * 
   * @param permissions - Permission list
   */
  setUserPermissions(permissions: string[]): void {
    this.config.userPermissions = permissions;
    this.denialReasons.clear();
  }
  
  /**
   * Check if navigation to the specified page is allowed
   * 
   * @param pageId - Target page ID
   * @param sourcePluginId - Plugin ID requesting navigation
   * @returns Whether navigation is allowed
   */
  canNavigate(pageId: string, sourcePluginId: string): boolean {
    // Clear previous denial reason
    this.denialReasons.delete(pageId);
    
    // 1. Check if page exists
    const pageInfo = this.pageRegistry.resolve(pageId);
    if (!pageInfo) {
      this.denialReasons.set(pageId, `Page ${pageId} does not exist`);
      return false;
    }
    
    // 2. Check if page is disabled
    if (this.config.disabledPages?.includes(pageId)) {
      this.denialReasons.set(pageId, `Page ${pageId} is disabled`);
      return false;
    }
    
    // 3. Check cross-plugin navigation
    if (!this.config.allowCrossPluginNavigation && 
        pageInfo.pluginId !== sourcePluginId) {
      this.denialReasons.set(
        pageId, 
        `Cross-plugin navigation from ${sourcePluginId} to ${pageInfo.pluginId} is not allowed`
      );
      return false;
    }
    
    // 4. Check page permissions
    if (!this.checkPermission(pageInfo)) {
      this.denialReasons.set(pageId, `No permission to access page ${pageId}`);
      return false;
    }
    
    return true;
  }
  
  /**
   * Get denial reason
   * 
   * @param pageId - Target page ID
   * @param sourcePluginId - Plugin ID requesting navigation
   * @returns Denial reason, or undefined if allowed
   */
  getDenialReason(pageId: string, sourcePluginId: string): string | undefined {
    // If no cached reason, recheck
    if (!this.denialReasons.has(pageId)) {
      this.canNavigate(pageId, sourcePluginId);
    }
    
    return this.denialReasons.get(pageId);
  }
  
  /**
   * Check page permissions
   * 
   * @param pageInfo - Page information
   * @returns Whether has permission
   */
  private checkPermission(pageInfo: PageInfo): boolean {
    const requiredPermissions = pageInfo.metadata?.permissions;
    
    // No permission required, allow access
    if (!requiredPermissions || requiredPermissions.length === 0) {
      return true;
    }
    
    const userPermissions = this.config.userPermissions ?? [];
    
    // Custom permission checker
    if (this.config.permissionChecker) {
      return this.config.permissionChecker(pageInfo.pageId, userPermissions);
    }
    
    // Default check: user has any of the required permissions
    return requiredPermissions.some(perm => userPermissions.includes(perm));
  }
}
