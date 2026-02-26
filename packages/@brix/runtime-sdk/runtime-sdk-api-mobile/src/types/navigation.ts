/**
 * @file Navigation related type definitions
 * @description Define core types for the navigation system, including navigation options, route change listeners, etc.
 * @module @brix/runtime-sdk-api-mobile/types/navigation
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Maintains consistent navigation system type definitions with runtime-sdk-api-web.
 * Mobile navigation implementation is based on React Navigation.
 *
 * [Design Notes]
 * - Define generic navigation contracts, router adapters implement specific logic
 * - Support declarative navigation (PageId) and imperative navigation (Path)
 */

// =========================================
// Navigation Options (Adapter Contract)
// =========================================

/**
 * Navigation Options
 *
 * <p>Configuration options to control navigation behavior.</p>
 */
export interface NavigateOptions {
  /**
   * Whether to replace current history entry
   *
   * <p>When true, the new page replaces the current page's position in the history stack.</p>
   */
  replace?: boolean;

  /**
   * Route state
   *
   * <p>State data passed to the target page.</p>
   */
  state?: Record<string, unknown>;

  /**
   * Route parameters
   * 
   * <p>Route parameters passed to the target page (commonly used in mobile).</p>
   */
  params?: Record<string, unknown>;
}

// =========================================
// Navigation Capability
// =========================================

/**
 * Navigation Capability Type Identifier
 */
export const NavigationCapabilityType = Symbol.for('NavigationCapability');

/**
 * Navigation Capability Contract
 *
 * <p>Provides page navigation capability for plugins, replacing direct use of React Navigation.</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const nav = context.getCapability<NavigationCapability>(NavigationCapabilityType);
 * nav.navigate('/booking/list');
 * nav.goBack();
 * ```
 */
export interface NavigationCapability {
  /**
   * Navigate to specified path
   *
   * @param path Target path
   * @param options Navigation options
   */
  navigate(path: string, options?: NavigateOptions): void;

  /**
   * Go back to previous page
   */
  goBack(): void;

  /**
   * Get current path
   *
   * @returns Current URL path
   */
  getCurrentPath(): string;

  /**
   * Reset navigation stack
   * 
   * <p>Mobile-specific: Reset entire navigation stack to specified path.</p>
   * 
   * @param path Target path
   * @param options Navigation options
   */
  reset?(path: string, options?: NavigateOptions): void;

  /**
   * Pop to specified path
   * 
   * <p>Mobile-specific: Pop navigation stack until specified path.</p>
   * 
   * @param path Target path
   */
  popTo?(path: string): void;
}

/**
 * Navigation Options (compatibility alias)
 */
export type NavigationOptions = NavigateOptions;

// =========================================
// Route Change Listener
// =========================================

/**
 * Route Change Listener
 *
 * <p>Used to listen for route change events.</p>
 */
export type RouteChangeListener = (path: string) => void;

// =========================================
// Router Capability (Compatibility Alias)
// =========================================

/**
 * Router Capability Type Identifier (compatibility alias)
 */
export const RouterCapabilityType = NavigationCapabilityType;

/**
 * Router Capability (compatibility alias)
 */
export type RouterCapability = NavigationCapability;
