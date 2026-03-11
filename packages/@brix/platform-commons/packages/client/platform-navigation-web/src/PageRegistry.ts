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
 * @file Page Registry
 * @description Manages PageId to URL mapping relationships
 * @module @brix/platform-navigation-web/PageRegistry
 * @version 3.0.0
 * 
 * [Architectural Notes]
 * PageRegistry is a core component of NavigationCapability.
 * Maintains bidirectional mapping between PageId and URL for unified page management.
 * 
 * [Responsibilities]
 * - Register page information (called by Host when loading plugins)
 * - Resolve URL from PageId
 * - Reverse lookup PageId from URL
 * - Support URL parameter substitution
 * 
 * [Data Source]
 * Page information comes from plugin ui-manifest.yaml declarations.
 * Host reads Manifest and registers to this registry when loading plugins.
 */

import type { PageInfo } from './types';

/**
 * Page Registry
 * 
 * Manages PageId to URL mapping relationships.
 * 
 * [Usage Example]
 * ```typescript
 * const registry = new PageRegistry();
 * 
 * // Host registers pages
 * registry.register({
 *   pageId: 'booking:list',
 *   urlPattern: '/booking/list',
 *   pluginId: 'booking',
 *   title: 'Booking List',
 * });
 * 
 * registry.register({
 *   pageId: 'booking:detail',
 *   urlPattern: '/booking/:id',
 *   pluginId: 'booking',
 *   title: 'Booking Detail',
 * });
 * 
 * // NavigationCapability usage
 * const pageInfo = registry.resolve('booking:detail');
 * // { pageId: 'booking:detail', urlPattern: '/booking/:id', ... }
 * 
 * const url = registry.buildUrl('booking:detail', { id: '123' });
 * // '/booking/123'
 * ```
 */
export class PageRegistry {
  /**
   * PageId to PageInfo mapping
   */
  private pageMap: Map<string, PageInfo> = new Map();
  
  /**
   * URL pattern to PageId mapping (for reverse lookup)
   */
  private urlToPageId: Map<string, string> = new Map();
  
  /**
   * Register a page
   * 
   * Called by Host when loading plugins to register pages declared in Manifest.
   * 
   * @param pageInfo - Page information
   * @throws Error if pageId already exists
   */
  register(pageInfo: PageInfo): void {
    const { pageId, urlPattern } = pageInfo;
    
    // Check for duplicate registration
    if (this.pageMap.has(pageId)) {
      throw new Error(
        `[PageRegistry] Page ID already exists: ${pageId}. ` +
        `Each page can only be registered once.`
      );
    }
    
    this.pageMap.set(pageId, pageInfo);
    this.urlToPageId.set(this.normalizePattern(urlPattern), pageId);
  }
  
  /**
   * Batch register pages
   * 
   * @param pages - Array of page information
   */
  registerMany(pages: PageInfo[]): void {
    for (const page of pages) {
      this.register(page);
    }
  }
  
  /**
   * Unregister a page
   * 
   * Called by Host when unloading plugins.
   * 
   * @param pageId - Page ID
   */
  unregister(pageId: string): void {
    const pageInfo = this.pageMap.get(pageId);
    
    if (pageInfo) {
      this.pageMap.delete(pageId);
      this.urlToPageId.delete(this.normalizePattern(pageInfo.urlPattern));
    }
  }
  
  /**
   * Unregister all pages for a plugin
   * 
   * @param pluginId - Plugin ID
   */
  unregisterByPlugin(pluginId: string): void {
    const toRemove: string[] = [];
    
    this.pageMap.forEach((pageInfo, pageId) => {
      if (pageInfo.pluginId === pluginId) {
        toRemove.push(pageId);
      }
    });
    
    for (const pageId of toRemove) {
      this.unregister(pageId);
    }
  }
  
  /**
   * Get page information by PageId
   * 
   * @param pageId - Page ID
   * @returns Page information, or undefined if not found
   */
  resolve(pageId: string): PageInfo | undefined {
    return this.pageMap.get(pageId);
  }
  
  /**
   * Get page information by URL
   * 
   * @param url - Page URL
   * @returns Page information, or undefined if not found
   */
  resolveByUrl(url: string): PageInfo | undefined {
    // Remove query params and hash
    const path = url.split('?')[0].split('#')[0];
    
    // Exact match
    const exactPageId = this.urlToPageId.get(path);
    if (exactPageId) {
      return this.pageMap.get(exactPageId);
    }
    
    // Pattern match (handle dynamic parameters)
    for (const [pattern, pageId] of this.urlToPageId) {
      if (this.matchPattern(pattern, path)) {
        return this.pageMap.get(pageId);
      }
    }
    
    return undefined;
  }
  
  /**
   * Build page URL
   * 
   * Generate actual URL from PageId and parameters.
   * 
   * @param pageId - Page ID
   * @param params - URL parameters
   * @returns Built URL
   * @throws Error if pageId does not exist
   */
  buildUrl(pageId: string, params?: Record<string, string | number>): string {
    const pageInfo = this.pageMap.get(pageId);
    
    if (!pageInfo) {
      throw new Error(`[PageRegistry] Unknown page ID: ${pageId}`);
    }
    
    let url = pageInfo.urlPattern;
    
    // Replace path parameters
    if (params) {
      const pathParams: Record<string, boolean> = {};
      
      url = url.replace(/:([^/]+)/g, (_, key) => {
        pathParams[key] = true;
        const value = params[key];
        
        if (value === undefined || value === null) {
          throw new Error(
            `[PageRegistry] Page ${pageId} is missing required path parameter: ${key}`
          );
        }
        
        return String(value);
      });
      
      // Remaining params as query string
      const queryParams = Object.entries(params)
        .filter(([key]) => !pathParams[key])
        .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
        .join('&');
      
      if (queryParams) {
        url += `?${queryParams}`;
      }
    }
    
    return url;
  }
  
  /**
   * Check if page exists
   * 
   * @param pageId - Page ID
   * @returns Whether exists
   */
  has(pageId: string): boolean {
    return this.pageMap.has(pageId);
  }
  
  /**
   * Get all registered pages
   * 
   * @returns Array of page information
   */
  getAll(): PageInfo[] {
    return Array.from(this.pageMap.values());
  }
  
  /**
   * Get all pages for a plugin
   * 
   * @param pluginId - Plugin ID
   * @returns Array of page information
   */
  getByPlugin(pluginId: string): PageInfo[] {
    return this.getAll().filter(page => page.pluginId === pluginId);
  }
  
  /**
   * Clear the registry
   */
  clear(): void {
    this.pageMap.clear();
    this.urlToPageId.clear();
  }
  
  /**
   * Normalize URL pattern
   * 
   * @param pattern - URL pattern
   * @returns Normalized pattern
   */
  private normalizePattern(pattern: string): string {
    // Ensure starts with /
    if (!pattern.startsWith('/')) {
      pattern = '/' + pattern;
    }
    
    // Remove trailing /
    if (pattern.length > 1 && pattern.endsWith('/')) {
      pattern = pattern.slice(0, -1);
    }
    
    return pattern;
  }
  
  /**
   * Check if URL matches pattern
   * 
   * @param pattern - URL pattern (e.g., /booking/:id)
   * @param url - Actual URL (e.g., /booking/123)
   * @returns Whether matches
   */
  private matchPattern(pattern: string, url: string): boolean {
    // Convert pattern to regex
    const regexStr = pattern
      .replace(/:[^/]+/g, '[^/]+')  // :id -> [^/]+
      .replace(/\*/g, '.*');         // * -> .*
    
    const regex = new RegExp(`^${regexStr}$`);
    return regex.test(url);
  }
}
