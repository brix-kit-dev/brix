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
 * @file Navigation-Related Type Definitions
 * @description Defines core types for the navigation system, including navigation options, route change listening, etc.
 * @module @brix-sdk/runtime-sdk-api-web/types/navigation
 * @version 3.2.0
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file, and promoted common contracts from infra-adapter-router-web.
 *
 * [v3.2.0 Phase 1 Additions]
 * - Added NavigateResult: Navigation result type
 * - Added WebNavigateOptions: Web navigation options
 * - Added PageChangeEvent: Page change event
 * - Added PageChangeHandler: Page change handler
 *
 * [Design Principles]
 * - Define common navigation contracts, router adapters implement specific logic
 * - Support declarative navigation (PageId) and imperative navigation (Path)
 */

import type { Unsubscribe } from './event';

// =========================================
// Navigation Options (Adapter Contract)
// =========================================

/**
 * Navigation Options
 *
 * <p>Configuration options controlling navigation behavior.</p>
 */
export interface NavigateOptions {
  /**
   * Whether to Replace Current History Entry
   *
   * <p>When true, the new page replaces the current page's position in the history stack.</p>
   */
  replace?: boolean;

  /**
   * Route State
   *
   * <p>State data passed to the target page.</p>
   */
  state?: Record<string, unknown>;
}

// =========================================
// Web Navigation Options (Extended)
// =========================================

/**
 * Web Navigation Options
 *
 * <p>Compared to basic NavigateOptions, provides richer navigation control.</p>
 *
 * @since 3.2.0
 */
export interface WebNavigateOptions extends NavigateOptions {
  /**
   * Whether to Open in New Window
   *
   * @default false
   */
  openInNewWindow?: boolean;

  /**
   * Skip Governance Policy Check
   *
   * <p>For Host internal use only, plugins setting this has no effect.</p>
   *
   * @internal
   */
  skipGovernance?: boolean;
}

// =========================================
// Navigation Result
// =========================================

/**
 * Navigation Result
 *
 * <p>Describes the execution result of a navigation request. In governance mode,
 * navigation initiated by plugins is a "request" not a "command", Host may reject navigation.</p>
 *
 * @since 3.2.0
 */
export interface NavigateResult {
  /**
   * Whether Navigation Succeeded
   */
  readonly success: boolean;

  /**
   * Failure Reason (only has value when success=false)
   *
   * - 'permission_denied': Insufficient permissions
   * - 'feature_disabled': Feature is disabled
   * - 'page_not_found': Page does not exist
   * - 'host_rejected': Host rejected navigation
   * - 'navigation_blocked': Navigation was blocked (e.g., unsaved form)
   */
  readonly reason?: 'permission_denied' | 'feature_disabled' | 'page_not_found' | 'host_rejected' | 'navigation_blocked';

  /**
   * Detailed Error Message
   */
  readonly message?: string;
}

// =========================================
// Page Change Event
// =========================================

/**
 * Page Change Event
 *
 * <p>Describes page switch details for page monitoring and analytics.</p>
 *
 * @since 3.2.0
 */
export interface PageChangeEvent {
  /**
   * Current Page ID
   *
   * <p>Format: {pluginId}:{pageName}, e.g., 'booking:detail'.</p>
   */
  readonly pageId: string;

  /**
   * Page Parameters
   *
   * <p>Parameter object passed to the target page.</p>
   */
  readonly params?: Record<string, unknown>;

  /**
   * Source Page ID (if any)
   */
  readonly fromPageId?: string;

  /**
   * Navigation Type
   *
   * - 'push': Forward navigation (clicking link/button)
   * - 'pop': Back navigation (browser back)
   * - 'replace': Replace navigation
   */
  readonly navigationType: 'push' | 'pop' | 'replace';
}

/**
 * Page Change Handler
 *
 * @since 3.2.0
 */
export type PageChangeHandler = (event: PageChangeEvent) => void;

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
 * <p>Provides page navigation capability for plugins, replacing direct use of react-router.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li>Plugins only perceive PageId, not URL</li>
 *   <li>All navigation is a "request", Host can reject</li>
 *   <li>Governance policies support permission checks, plugin isolation, Feature Flags</li>
 * </ul>
 *
 * <h3>Basic Usage (Imperative Navigation)</h3>
 * ```typescript
 * const nav = context.getCapability<NavigationCapability>(NavigationCapabilityType);
 * nav.navigate('/booking/list');
 * nav.goBack();
 * ```
 *
 * <h3>Advanced Usage (Request-Based Navigation)</h3>
 * ```typescript
 * const nav = context.getCapability<NavigationCapability>(NavigationCapabilityType);
 *
 * // Request navigation to page (may be rejected)
 * const result = await nav.requestNavigate('booking:detail', { id: '123' });
 * if (!result.success) {
 *   console.error('Navigation failed:', result.reason, result.message);
 * }
 *
 * // Subscribe to page changes
 * const unsubscribe = nav.onPageChange((event) => {
 *   console.log('Page changed:', event.pageId);
 * });
 * ```
 *
 * @since 3.0.0
 */
export interface NavigationCapability {
  // =========================================
  // Basic Navigation Methods (Imperative, Directly Executed)
  // =========================================

  /**
   * Navigate to Specified Path
   *
   * <p>Imperative navigation, executed directly. For controlled navigation, use requestNavigate.</p>
   *
   * @param path Target path
   * @param options Navigation options
   */
  navigate(path: string, options?: NavigateOptions): void;

  /**
   * Go Back to Previous Page
   */
  goBack(): void;

  /**
   * Get Current Path
   *
   * @returns Current URL path
   */
  getCurrentPath(): string;

  // =========================================
  // Advanced Navigation Methods (Request-Based, Governable)
  // =========================================

  /**
   * Request Navigation to Specified Page
   *
   * <p>This is a "request" not a "command", Host can reject navigation based on governance policies.</p>
   *
   * @param pageId Target page ID (format: {pluginId}:{pageName})
   * @param params Page parameters
   * @param options Navigation options
   * @returns Navigation result
   *
   * @since 3.2.0
   */
  requestNavigate?(
    pageId: string,
    params?: Record<string, unknown>,
    options?: WebNavigateOptions
  ): Promise<NavigateResult>;

  /**
   * Check If Navigation to Specified Page Is Possible
   *
   * <p>Pre-check, does not execute actual navigation.</p>
   *
   * @param pageId Target page ID
   * @returns Whether navigation is possible
   *
   * @since 3.2.0
   */
  canNavigate?(pageId: string): boolean;

  /**
   * Get Current Page ID
   *
   * @returns Current page ID, returns empty string if unrecognized
   *
   * @since 3.2.0
   */
  getCurrentPageId?(): string;

  /**
   * Get Current Page Parameters
   *
   * @typeParam T Parameter type
   * @returns Page parameter object
   *
   * @since 3.2.0
   */
  getPageParams?<T extends Record<string, unknown> = Record<string, unknown>>(): T;

  /**
   * Request Go Back to Previous Page
   *
   * @returns Navigation result
   *
   * @since 3.2.0
   */
  requestGoBack?(): Promise<NavigateResult>;

  /**
   * Subscribe to Page Change Events
   *
   * @param handler Page change handler
   * @returns Unsubscribe function
   *
   * @since 3.2.0
   */
  onPageChange?(handler: PageChangeHandler): Unsubscribe;
}

/**
 * Compatibility alias for older shell code that still refers to Router as the
 * concrete navigation capability. The canonical contract is NavigationCapability.
 */
export const RouterCapabilityType = NavigationCapabilityType;

/** Compatibility alias for NavigationCapability. */
export type RouterCapability = NavigationCapability;

/**
 * Navigation Options (Compatibility Alias)
 */
export type NavigationOptions = NavigateOptions;

// =========================================
// Route Change Listening
// =========================================

/**
 * Route Change Listener
 *
 * <p>Used to listen for route change events.</p>
 */
export type RouteChangeListener = (path: string) => void;
