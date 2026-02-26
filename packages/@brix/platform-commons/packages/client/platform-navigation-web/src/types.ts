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
