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
 * @file Page Information Definitions
 * @description Defines page information structure in the page registry
 * @module @brix/platform-navigation-web/types
 * @version 3.0.0
 */

/**
 * Page Information
 * 
 * Describes complete information for a registered page
 */
export interface PageInfo {
  /**
   * Page ID
   * 
   * Format: {pluginId}:{pageName}
   * Examples: booking:list, identity:profile
   */
  pageId: string;
  
  /**
   * Page URL pattern
   * 
   * Pattern string for generating actual URLs
   * Supports parameter placeholders, e.g., /booking/:id
   */
  urlPattern: string;
  
  /**
   * Page title
   */
  title?: string;
  
  /**
   * Plugin ID this page belongs to
   */
  pluginId: string;
  
  /**
   * Page metadata
   */
  metadata?: PageMetadata;
}

/**
 * Page Metadata
 */
export interface PageMetadata {
  /**
   * Page icon
   */
  icon?: string;
  
  /**
   * Required permission list
   */
  permissions?: string[];
  
  /**
   * Whether authentication is required
   */
  requireAuth?: boolean;
  
  /**
   * Page description
   */
  description?: string;
  
  /**
   * Breadcrumb configuration
   */
  breadcrumb?: {
    title: string;
    parent?: string;
  };
  
  /**
   * Other custom metadata
   */
  [key: string]: unknown;
}

/**
 * Navigation Governance Policy
 */
export interface GovernancePolicy {
  /**
   * Check if navigation to the specified page is allowed
   * 
   * @param pageId - Target page ID
   * @param sourcePluginId - Plugin ID requesting navigation
   * @returns Whether navigation is allowed
   */
  canNavigate(pageId: string, sourcePluginId: string): boolean;
  
  /**
   * Get denial reason
   * 
   * @param pageId - Target page ID
   * @param sourcePluginId - Plugin ID requesting navigation
   * @returns Denial reason, or undefined if allowed
   */
  getDenialReason(pageId: string, sourcePluginId: string): string | undefined;
}
