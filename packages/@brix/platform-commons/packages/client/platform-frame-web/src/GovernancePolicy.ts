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
 * @file Layout Governance Policy
 * @description Layout request permission checks
 * @module @brix-sdk/platform-frame-web/GovernancePolicy
 * @version 3.0.0
 * 
 * [Design Notes]
 * LayoutGovernancePolicy determines whether to approve plugin layout requests.
 * 
 * [Check Contents]
 * 1. Check if feature is allowed (fullscreen, hide sidebar, etc.)
 * 2. Check if plugin is in allowed list
 * 3. Check if plugin is in blocked list
 * 4. Return rejection reason
 */

import type { LayoutRequestResult } from '@brix-sdk/runtime-sdk-api-web';
import type { LayoutGovernancePolicy, LayoutChangeRequest } from './layout-types';

/**
 * Request type
 */
type RequestType = 'fullscreen' | 'hideSidebar' | 'hideHeader' | 'collapseSidebar';

/**
 * Governance Policy Handler
 * 
 * Layout request permission check utility.
 * 
 * [Usage Example]
 * ```typescript
 * const governance = new GovernancePolicyHandler({
 *   allowFullscreen: true,
 *   allowHideSidebar: true,
 *   allowHideHeader: false,
 *   blockedPlugins: ['untrusted-plugin'],
 * });
 * 
 * const result = governance.checkRequest({
 *   type: 'fullscreen',
 *   pluginId: 'my-plugin',
 *   changes: { isFullscreen: true },
 *   timestamp: Date.now(),
 * });
 * 
 * if (!result.success) {
 *   console.log('Request rejected:', result.reason);
 * }
 * ```
 */
export class GovernancePolicyHandler {
  /**
   * Current policy
   */
  private policy: LayoutGovernancePolicy;
  
  /**
   * Constructor
   * 
   * @param policy - Governance policy
   */
  constructor(policy: LayoutGovernancePolicy = {}) {
    this.policy = {
      allowFullscreen: true,
      allowHideSidebar: true,
      allowHideHeader: false,
      ...policy,
    };
  }
  
  /**
   * Check if layout request is allowed
   * 
   * @param request - Layout request
   * @returns Check result
   */
  checkRequest(request: LayoutChangeRequest): LayoutRequestResult {
    const { pluginId, changes } = request;
    
    // 1. Check if plugin is blocked
    if (this.isPluginBlocked(pluginId)) {
      return {
        success: false,
        reason: 'policy_denied',
        message: `Plugin ${pluginId} is prohibited from modifying layout`,
      };
    }
    
    // 2. Check if plugin is in allowed list (if whitelist exists)
    if (!this.isPluginAllowed(pluginId)) {
      return {
        success: false,
        reason: 'policy_denied',
        message: `Plugin ${pluginId} is not in allowed list`,
      };
    }
    
    // 3. Check if specific features are allowed
    if (changes.isFullscreen !== undefined) {
      const result = this.checkFeature('fullscreen', pluginId);
      if (!result.success) return result;
    }
    
    if (changes.isSidebarVisible === false) {
      const result = this.checkFeature('hideSidebar', pluginId);
      if (!result.success) return result;
    }
    
    if (changes.isHeaderVisible === false) {
      const result = this.checkFeature('hideHeader', pluginId);
      if (!result.success) return result;
    }
    
    return { success: true };
  }
  
  /**
   * Check if plugin is blocked
   * 
   * @param pluginId - Plugin ID
   * @returns Whether blocked
   */
  private isPluginBlocked(pluginId: string): boolean {
    return this.policy.blockedPlugins?.includes(pluginId) ?? false;
  }
  
  /**
   * Check if plugin is allowed
   * 
   * @param pluginId - Plugin ID
   * @returns Whether allowed
   */
  private isPluginAllowed(pluginId: string): boolean {
    // If no whitelist is configured, allow all plugins
    if (!this.policy.allowedPlugins || this.policy.allowedPlugins.length === 0) {
      return true;
    }
    
    return this.policy.allowedPlugins.includes(pluginId);
  }
  
  /**
   * Check if feature is allowed
   * 
   * @param feature - Feature type
   * @param _pluginId - Plugin ID (for logging, extensible for future use)
   * @returns Check result
   */
  private checkFeature(feature: RequestType, _pluginId: string): LayoutRequestResult {
    switch (feature) {
      case 'fullscreen':
        if (this.policy.allowFullscreen === false) {
          return {
            success: false,
            reason: 'policy_denied',
            message: 'Fullscreen feature is disabled',
          };
        }
        break;
        
      case 'hideSidebar':
        if (this.policy.allowHideSidebar === false) {
          return {
            success: false,
            reason: 'policy_denied',
            message: 'Hide sidebar feature is disabled',
          };
        }
        break;
        
      case 'hideHeader':
        if (this.policy.allowHideHeader === false) {
          return {
            success: false,
            reason: 'policy_denied',
            message: 'Hide header feature is disabled',
          };
        }
        break;
    }
    
    return { success: true };
  }
  
  /**
   * Update policy
   * 
   * @param policy - New policy configuration
   */
  updatePolicy(policy: Partial<LayoutGovernancePolicy>): void {
    this.policy = { ...this.policy, ...policy };
  }
  
  /**
   * Get current policy
   * 
   * @returns Current governance policy
   */
  getPolicy(): LayoutGovernancePolicy {
    return { ...this.policy };
  }
}
