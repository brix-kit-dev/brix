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
 * @file Layout Capability Type Definitions
 * @description Defines core types for the layout system, including sidebar, header, fullscreen, and other layout controls
 * @module @brix-sdk/runtime-sdk-api-web/types/layout
 * @version 3.2.0
 *
 * [v3.2.0 Added]
 * Phase 1 contract layer fix: Promoted the LayoutCapability interface from shell-web to runtime-sdk-api-web.
 *
 * [Design Principles]
 * - Layout is entirely controlled by the Host
 * - Plugins can only "request" layout behavior, Host decides whether to respond
 * - All requests go through governance policy checks
 *
 * [Architectural Constraints]
 * ? Directly manipulating document.body is prohibited
 * ? Creating global Portals to body is prohibited
 * ? Modifying global CSS (like overflow) is prohibited
 * ? Request layout changes through LayoutCapability or useLayout hook
 */

import type { Unsubscribe } from './event';

// =========================================
// Layout Mode
// =========================================

/**
 * Layout Mode
 *
 * - 'console': Console layout (with sidebar and header)
 * - 'portal': Portal layout (simplified header, no sidebar)
 * - 'minimal': Minimal layout (content area only)
 */
export type LayoutMode = 'console' | 'portal' | 'minimal';

// =========================================
// Layout State
// =========================================

/**
 * Layout State (Capability Contract)
 *
 * <p>Describes the complete state of the current layout.</p>
 * <p>This is the readonly capability state exposed to plugins via LayoutCapability.</p>
 * 
 * [Note: Canonical Definition]
 * This is the canonical LayoutState for the runtime capability layer.
 * Shell layers may define their own simplified LayoutState with mutable fields
 * for local UI component state (e.g., brix-platform-shell-web/AppLayout.tsx).
 * Those are intentionally different as they serve different architectural layers.
 */
export interface LayoutState {
  /** Whether in Fullscreen Mode */
  readonly fullscreen: boolean;

  /** Whether Sidebar is Visible */
  readonly sidebarVisible: boolean;

  /** Whether Sidebar is Collapsed */
  readonly sidebarCollapsed: boolean;

  /** Whether Header is Visible */
  readonly headerVisible: boolean;

  /** Whether Footer is Visible */
  readonly footerVisible: boolean;

  /** Current Layout Mode */
  readonly layoutMode: LayoutMode;

  /** Current Breakpoint */
  readonly breakpoint: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | 'xxl';

  /** Whether in Mobile View */
  readonly isMobile: boolean;

  /** Sidebar Width (expanded state) */
  readonly sidebarWidth: number;

  /** Sidebar Width (collapsed state) */
  readonly sidebarCollapsedWidth: number;

  /** Header Height */
  readonly headerHeight: number;

  /** Content Area Available Width */
  readonly contentWidth?: number;

  /** Content Area Available Height */
  readonly contentHeight?: number;
}

// =========================================
// Layout Request Result
// =========================================

/**
 * Layout Request Result
 *
 * <p>Describes the execution result of a layout change request.</p>
 */
export interface LayoutRequestResult {
  /**
   * Whether the Request Succeeded
   */
  readonly success: boolean;

  /**
   * Failure Reason
   *
   * - 'policy_denied': Governance policy denied
   * - 'not_supported': Current layout mode does not support this operation
   * - 'already_applied': Already in the target state
   */
  readonly reason?: 'policy_denied' | 'not_supported' | 'already_applied';

  /**
   * Detailed Message
   */
  readonly message?: string;
}

// =========================================
// Layout Change Event
// =========================================

/**
 * Layout Change Event
 *
 * <p>Triggered when layout state changes.</p>
 */
export interface LayoutChangeEvent {
  /** Change Type */
  readonly type: 'fullscreen' | 'sidebar' | 'header' | 'footer' | 'breakpoint' | 'mode';

  /** New State */
  readonly state: LayoutState;

  /** Previous State */
  readonly previousState: LayoutState;

  /** Change Source */
  readonly source: 'user' | 'plugin' | 'system';

  /** Plugin ID that initiated the change (if source is plugin) */
  readonly pluginId?: string;

  /** Change Timestamp */
  readonly timestamp: number;
}

/**
 * Layout Change Handler
 */
export type LayoutChangeHandler = (event: LayoutChangeEvent) => void;

// =========================================
// Layout Capability
// =========================================

/**
 * Layout Capability Type Identifier
 */
export const LayoutCapabilityType = Symbol.for('LayoutCapability');

/**
 * Layout Capability Contract
 *
 * <p>Provides layout control capability for plugins, including fullscreen, sidebar, header, etc.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li>Layout is entirely controlled by the Host</li>
 *   <li>Plugins can only "request" layout behavior, Host decides whether to respond</li>
 *   <li>All requests go through governance policy checks</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const layout = context.getCapability<LayoutCapability>(LayoutCapabilityType);
 *
 * // Request fullscreen
 * const success = await layout.requestFullscreen();
 *
 * // Get current state
 * const state = layout.getState();
 * if (state.isMobile) {
 *   // Mobile handling...
 * }
 *
 * // Subscribe to layout changes
 * const unsubscribe = layout.onLayoutChange((event) => {
 *   console.log(`Layout change: ${event.type}`);
 * });
 * ```
 *
 * @since 3.2.0
 */
export interface LayoutCapability {
  // =========================================
  // State Retrieval
  // =========================================

  /**
   * Get Current Layout State
   *
   * @returns Layout state object
   */
  getState(): LayoutState;

  /**
   * Check if Fullscreen
   *
   * @returns Whether in fullscreen mode
   */
  isFullscreen(): boolean;

  /**
   * Check if Sidebar is Visible
   *
   * @returns Whether visible
   */
  isSidebarVisible(): boolean;

  /**
   * Check if Sidebar is Collapsed
   *
   * @returns Whether collapsed
   */
  isSidebarCollapsed(): boolean;

  // =========================================
  // Fullscreen Control
  // =========================================

  /**
   * Request Enter Fullscreen Mode
   *
   * @returns Whether successful
   */
  requestFullscreen(): Promise<boolean>;

  /**
   * Request Exit Fullscreen Mode
   *
   * @returns Whether successful
   */
  requestExitFullscreen(): Promise<boolean>;

  // =========================================
  // Sidebar Control
  // =========================================

  /**
   * Request Hide Sidebar
   *
   * @returns Whether successful
   */
  requestHideSidebar(): Promise<boolean>;

  /**
   * Request Show Sidebar
   *
   * @returns Whether successful
   */
  requestShowSidebar(): Promise<boolean>;

  /**
   * Request Collapse Sidebar
   *
   * @returns Whether successful
   */
  requestCollapseSidebar?(): Promise<boolean>;

  /**
   * Request Expand Sidebar
   *
   * @returns Whether successful
   */
  requestExpandSidebar?(): Promise<boolean>;

  /**
   * Request Toggle Sidebar Collapse State
   *
   * @returns Whether successful
   */
  requestToggleSidebar?(): Promise<boolean>;

  // =========================================
  // Header/Footer Control
  // =========================================

  /**
   * Request Hide Header
   *
   * @returns Whether successful
   */
  requestHideHeader?(): Promise<boolean>;

  /**
   * Request Show Header
   *
   * @returns Whether successful
   */
  requestShowHeader?(): Promise<boolean>;

  /**
   * Request Hide Footer
   *
   * @returns Whether successful
   */
  requestHideFooter?(): Promise<boolean>;

  /**
   * Request Show Footer
   *
   * @returns Whether successful
   */
  requestShowFooter?(): Promise<boolean>;

  // =========================================
  // Comprehensive Layout Control
  // =========================================

  /**
   * Request Layout Change
   *
   * <p>Can change multiple layout parameters simultaneously.</p>
   *
   * @param changes Layout change parameters
   * @returns Request result
   */
  requestLayoutChange?(changes: Partial<{
    fullscreen: boolean;
    sidebarVisible: boolean;
    sidebarCollapsed: boolean;
    headerVisible: boolean;
    footerVisible: boolean;
  }>): Promise<LayoutRequestResult>;

  /**
   * Switch Layout Mode
   *
   * @param mode Target layout mode
   * @returns Whether successful
   */
  setLayoutMode?(mode: LayoutMode): Promise<boolean>;

  // =========================================
  // Responsive Information
  // =========================================

  /**
   * Get Current Breakpoint
   *
   * @returns Breakpoint name
   */
  getBreakpoint?(): 'xs' | 'sm' | 'md' | 'lg' | 'xl' | 'xxl';

  /**
   * Check if Mobile View
   *
   * @returns Whether mobile
   */
  isMobileView?(): boolean;

  // =========================================
  // Event Subscription
  // =========================================

  /**
   * Subscribe to Layout Change Event
   *
   * @param handler Event handler
   * @returns Unsubscribe function
   */
  onLayoutChange?(handler: LayoutChangeHandler): Unsubscribe;
}
