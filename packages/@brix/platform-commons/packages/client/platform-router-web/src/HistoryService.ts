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
 * @file Browser History Service
 * @description Provides browser history management functionality
 * @module @brix-sdk/platform-router-web/HistoryService
 * @version 3.0.0
 * 
 * ¡¾Architecture Notes¡¿
 * HistoryService provides advanced browser history management features,
 * including history tracking, navigation interception, navigation confirmation, etc.
 * 
 * ¡¾Usage Scenarios¡¿
 * - Block navigation when form is unsaved
 * - Track user navigation path
 * - Implement "return to home" functionality
 */

/**
 * History Entry
 */
export interface HistoryEntry {
  /**
   * Page URL
   */
  url: string;
  
  /**
   * Page title
   */
  title: string;
  
  /**
   * Entry timestamp
   */
  timestamp: number;
  
  /**
   * Additional state
   */
  state?: unknown;
}

/**
 * Navigation Interceptor
 * 
 * @param from - Current URL
 * @param to - Target URL
 * @returns Whether to allow navigation (true to allow, false to block)
 */
export type NavigationInterceptor = (from: string, to: string) => boolean | Promise<boolean>;

/**
 * Unsubscribe Function
 */
export type Unsubscribe = () => void;

/**
 * Browser History Service
 * 
 * Provides history tracking and navigation interception functionality.
 * 
 * ¡¾Features¡¿
 * - Maintains navigation history within the application
 * - Supports navigation interception (e.g., unsaved confirmation)
 * - Provides advanced navigation features like "return to home"
 */
export class HistoryService {
  /**
   * History entries
   */
  private history: HistoryEntry[] = [];
  
  /**
   * Maximum history entries
   */
  private maxSize: number;
  
  /**
   * Navigation interceptor set
   */
  private interceptors: Set<NavigationInterceptor> = new Set();
  
  /**
   * Home URL
   */
  private homeUrl: string;
  
  /**
   * Constructor
   * 
   * @param options - Configuration options
   */
  constructor(options: {
    maxSize?: number;
    homeUrl?: string;
  } = {}) {
    this.maxSize = options.maxSize ?? 50;
    this.homeUrl = options.homeUrl ?? '/';
    
    // Record current page as first history entry
    this.recordEntry(window.location.pathname + window.location.search);
  }
  
  /**
   * Record history entry
   * 
   * @param url - Page URL
   * @param title - Page title (optional, defaults to document.title)
   * @param state - Additional state
   */
  recordEntry(url: string, title?: string, state?: unknown): void {
    const entry: HistoryEntry = {
      url,
      title: title ?? document.title ?? url,
      timestamp: Date.now(),
      state,
    };
    
    this.history.push(entry);
    
    // Remove earliest entry when exceeding max size
    if (this.history.length > this.maxSize) {
      this.history.shift();
    }
  }
  
  /**
   * Get history entries
   * 
   * @param limit - Limit number of returned entries
   * @returns History entries array (newest first)
   */
  getHistory(limit?: number): HistoryEntry[] {
    const entries = [...this.history].reverse();
    
    if (limit && limit > 0) {
      return entries.slice(0, limit);
    }
    
    return entries;
  }
  
  /**
   * Get previous page
   * 
   * @returns Previous page info, or undefined if none
   */
  getPreviousPage(): HistoryEntry | undefined {
    if (this.history.length < 2) {
      return undefined;
    }
    
    return this.history[this.history.length - 2];
  }
  
  /**
   * Check if can go back
   * 
   * @returns Whether there are history entries to go back to
   */
  canGoBack(): boolean {
    return this.history.length > 1;
  }
  
  /**
   * Get home URL
   * 
   * @returns Configured home URL
   */
  getHomeUrl(): string {
    return this.homeUrl;
  }
  
  /**
   * Set home URL
   * 
   * @param url - Home URL
   */
  setHomeUrl(url: string): void {
    this.homeUrl = url;
  }
  
  /**
   * Add navigation interceptor
   * 
   * Interceptors can block navigation (e.g., confirm when form unsaved).
   * 
   * ¡¾Usage Example¡¿
   * ```typescript
   * const unsubscribe = historyService.addInterceptor(async (from, to) => {
   *   if (formDirty) {
   *     const confirmed = await confirm('You have unsaved changes, are you sure you want to leave?');
   *     return confirmed;
   *   }
   *   return true;
   * });
   * ```
   * 
   * @param interceptor - Interceptor function
   * @returns Unsubscribe function
   */
  addInterceptor(interceptor: NavigationInterceptor): Unsubscribe {
    this.interceptors.add(interceptor);
    
    return () => {
      this.interceptors.delete(interceptor);
    };
  }
  
  /**
   * Execute navigation interception check
   * 
   * Calls all interceptors in sequence, blocks navigation if any returns false.
   * 
   * @param from - Current URL
   * @param to - Target URL
   * @returns Whether navigation is allowed
   */
  async checkInterceptors(from: string, to: string): Promise<boolean> {
    for (const interceptor of this.interceptors) {
      try {
        const allowed = await interceptor(from, to);
        
        if (!allowed) {
          return false;
        }
      } catch (error) {
        console.error('[HistoryService] Interceptor execution error:', error);
        // Default to allow navigation when interceptor errors
      }
    }
    
    return true;
  }
  
  /**
   * Clear history
   */
  clear(): void {
    this.history = [];
  }
  
  /**
   * Destroy service
   */
  destroy(): void {
    this.history = [];
    this.interceptors.clear();
  }
}
