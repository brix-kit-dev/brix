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
 * @file Router Service Interface Definition
 * @description Defines the unified interface for platform internal router service, for capability implementation layer use
 * @module @brix-sdk/platform-router-web/RouterService
 * @version 3.0.0
 * 
 * ¡¾Architecture Notes¡¿
 * RouterService is the platform's internal router service interface, not exposed to plugins.
 * 
 * ¡¾Responsibility Boundaries¡¿
 * - Encapsulates implementation details of underlying routing library (react-router-dom)
 * - Provides unified routing operation interface
 * - Only for internal use by platform modules like platform-navigation-web
 * 
 * ¡¾Architectural Constraints¡¿
 * ? Forbidden to use this service directly in plugins
 * ? Forbidden to import this module in code outside Host layer
 * ? Plugins can only use routing functionality indirectly through NavigationCapability
 */

/**
 * URL Change Listener
 * 
 * @param url - The new URL path
 */
export type UrlChangeListener = (url: string) => void;

/**
 * Unsubscribe Function
 */
export type Unsubscribe = () => void;

/**
 * Navigation Options
 */
export interface NavigateOptions {
  /**
   * Whether to replace current history entry
   * 
   * When set to true, no new entry is added to browser history
   * @default false
   */
  replace?: boolean;
  
  /**
   * State data to pass to target page
   * 
   * Can be retrieved via useLocation().state
   */
  state?: unknown;
}

/**
 * Router Service Interface
 * 
 * Unified interface for platform internal routing operations.
 * 
 * ¡¾Important¡¿
 * This is an internal platform service, NOT exposed to plugins!
 * Plugins can only request navigation through NavigationCapability.
 * 
 * ¡¾Design Rationale¡¿
 * 1. Routing is Host's core control point, plugins cannot directly operate
 * 2. Through capability layer abstraction, navigation governance can be implemented (permission checks, logging, etc.)
 * 3. Plugins don't need to care about underlying routing library implementation
 */
export interface RouterService {
  /**
   * Navigate to specified URL
   * 
   * @param url - Target URL path
   * @param options - Navigation options
   */
  navigate(url: string, options?: NavigateOptions): void;
  
  /**
   * Replace current URL (no history entry)
   * 
   * @param url - Target URL path
   * @param state - State data to pass to target page
   */
  replace(url: string, state?: unknown): void;
  
  /**
   * Go back to previous page
   * 
   * Equivalent to browser back button
   */
  goBack(): void;
  
  /**
   * Go forward to next page
   * 
   * Equivalent to browser forward button
   */
  goForward(): void;
  
  /**
   * Navigate to specific position in history
   * 
   * @param delta - Offset relative to current position (negative for back, positive for forward)
   */
  go(delta: number): void;
  
  /**
   * Get current URL
   * 
   * @returns Current URL path (including query string)
   */
  getCurrentUrl(): string;
  
  /**
   * Get current path
   * 
   * @returns Current path (without query string and hash)
   */
  getCurrentPath(): string;
  
  /**
   * Get current query parameters
   * 
   * @returns Query parameters object
   */
  getQueryParams(): Record<string, string>;
  
  /**
   * Get current hash
   * 
   * @returns Current hash (without # sign)
   */
  getHash(): string;
  
  /**
   * Subscribe to URL change events
   * 
   * @param listener - URL change listener
   * @returns Unsubscribe function
   */
  onUrlChange(listener: UrlChangeListener): Unsubscribe;
  
  /**
   * Check if specified path matches current URL
   * 
   * @param pattern - Path pattern (supports * wildcard)
   * @returns Whether it matches
   */
  isActive(pattern: string): boolean;
}
