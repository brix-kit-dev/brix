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
 * @file Layout State Store
 * @description Manages layout state
 * @module @brix-sdk/platform-frame-web/LayoutStore
 * @version 3.1.0
 * 
 * [Design Notes]
 * LayoutStore is the core storage for layout state.
 * 
 * [Responsibilities]
 * 1. Store layout state
 * 2. Respond to viewport changes
 * 3. Calculate breakpoints
 * 4. Emit state change events
 * 
 * [Architecture Notes]
 * Implements v3.0 architecture diagram 4.6-1 requirements.
 * Unifies @brix-sdk/platform-design-tokens and shell-web LayoutStore breakpoint values.
 */

import type { LayoutState, LayoutChangeEvent, LayoutChangeHandler, Unsubscribe } from '@brix-sdk/runtime-sdk-api-web';
import type { LayoutConfig } from './layout-types';
import { breakpointValues, type BreakpointKey } from '@brix-sdk/platform-design-tokens';

/**
 * Breakpoint configuration type (from @brix-sdk/platform-design-tokens)
 */
type BreakpointConfig = typeof breakpointValues;

/**
 * Default breakpoint configuration (from @brix-sdk/platform-design-tokens)
 * 
 * | Breakpoint | Min Width | Device Type |
 * |------------|-----------|-------------|
 * | xs         | 0         | Phone (portrait) |
 * | sm         | 576px     | Phone (landscape) |
 * | md         | 768px     | Tablet (portrait) |
 * | lg         | 992px     | Tablet (landscape)/Small laptop |
 * | xl         | 1200px    | Desktop |
 * | xxl        | 1600px    | Large desktop |
 */
const DEFAULT_BREAKPOINTS: BreakpointConfig = breakpointValues;

/**
 * Calculate current breakpoint
 * 
 * @param width - Viewport width
 * @param breakpoints - Breakpoint configuration (optional)
 * @returns Current breakpoint
 */
function calculateBreakpoint(
  width: number,
  breakpoints?: Partial<BreakpointConfig>
): LayoutState['breakpoint'] {
  const bp: BreakpointConfig = { ...DEFAULT_BREAKPOINTS, ...breakpoints };
  if (width >= bp.xxl) return 'xxl';
  if (width >= bp.xl) return 'xl';
  if (width >= bp.lg) return 'lg';
  if (width >= bp.md) return 'md';
  if (width >= bp.sm) return 'sm';
  return 'xs';
}

/**
 * Layout State Store
 * 
 * Manages the layout state for the entire application.
 * 
 * [Usage Example]
 * ```typescript
 * const layoutStore = new LayoutStore({
 *   layoutMode: 'console',
 *   defaultSidebarVisible: true,
 * });
 * 
 * // Subscribe to changes
 * layoutStore.subscribe((event) => {
 *   console.log('Layout changed:', event);
 * });
 * 
 * // Update state
 * layoutStore.update({ isSidebarVisible: false }, 'user');
 * ```
 */
export class LayoutStore {
  /**
   * Current layout state
   */
  private state: LayoutState;
  
  /**
   * Listener list
   */
  private listeners: Set<LayoutChangeHandler> = new Set();
  
  /**
   * Configuration
   */
  private config: LayoutConfig;
  
  /**
   * Viewport change observer
   */
  private resizeObserver: ResizeObserver | null = null;
  
  /**
   * Constructor
   * 
   * @param config - Layout configuration
   */
  constructor(config: LayoutConfig = {}) {
    this.config = config;
    
    // Get initial viewport dimensions
    const viewportWidth = typeof window !== 'undefined' ? window.innerWidth : 1200;
    const viewportHeight = typeof window !== 'undefined' ? window.innerHeight : 800;
    
    // Calculate component dimensions
    const sidebarWidth = config.defaultSidebarVisible !== false
      ? (config.defaultSidebarCollapsed 
          ? (config.sidebarCollapsedWidth ?? 80)
          : (config.sidebarWidth ?? 256))
      : 0;
    const headerHeight = config.defaultHeaderVisible !== false 
      ? (config.headerHeight ?? 64) 
      : 0;
    const footerHeight = config.defaultFooterVisible !== false 
      ? (config.footerHeight ?? 0) 
      : 0;
    
    // Initialize state
    this.state = {
      isFullscreen: false,
      isSidebarVisible: config.defaultSidebarVisible ?? true,
      isSidebarCollapsed: config.defaultSidebarCollapsed ?? false,
      isHeaderVisible: config.defaultHeaderVisible ?? true,
      isFooterVisible: config.defaultFooterVisible ?? true,
      viewportWidth,
      viewportHeight,
      breakpoint: calculateBreakpoint(viewportWidth, config.breakpoints),
      layoutMode: config.layoutMode ?? 'console',
      contentHeight: viewportHeight - headerHeight - footerHeight,
      contentWidth: viewportWidth - sidebarWidth,
    };
    
    // Listen to viewport changes
    if (typeof window !== 'undefined') {
      this.setupResizeObserver();
    }
  }
  
  /**
   * Set up viewport change listener
   */
  private setupResizeObserver(): void {
    // Use ResizeObserver to observe body changes
    this.resizeObserver = new ResizeObserver(() => {
      this.handleResize();
    });
    
    if (document.body) {
      this.resizeObserver.observe(document.body);
    }
    
    // Also listen to window resize event as fallback
    window.addEventListener('resize', this.handleResize.bind(this));
  }
  
  /**
   * Handle viewport change
   */
  private handleResize = (): void => {
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;
    
    const newBreakpoint = calculateBreakpoint(viewportWidth, this.config.breakpoints);
    
    const changedProperties: (keyof LayoutState)[] = [];
    
    if (this.state.viewportWidth !== viewportWidth) {
      changedProperties.push('viewportWidth');
    }
    if (this.state.viewportHeight !== viewportHeight) {
      changedProperties.push('viewportHeight');
    }
    if (this.state.breakpoint !== newBreakpoint) {
      changedProperties.push('breakpoint');
    }
    
    if (changedProperties.length > 0) {
      this.update({
        viewportWidth,
        viewportHeight,
        breakpoint: newBreakpoint,
      }, 'system');
    }
  };
  
  /**
   * Get current state
   * 
   * @returns Current layout state
   */
  getState(): LayoutState {
    return { ...this.state };
  }
  
  /**
   * Update state
   * 
   * @param changes - State changes
   * @param reason - Change reason
   * @param requestedBy - Requesting plugin ID
   */
  update(
    changes: Partial<LayoutState>,
    reason: 'user' | 'system' | 'plugin',
    requestedBy?: string
  ): void {
    // Filter unchanged properties
    const changedProperties = (Object.keys(changes) as (keyof LayoutState)[])
      .filter(key => this.state[key] !== changes[key]);
    
    if (changedProperties.length === 0) {
      return;
    }
    
    // Update state
    const oldState = { ...this.state };
    this.state = { ...this.state, ...changes };
    
    // Recalculate content area dimensions
    this.recalculateContentArea();
    
    // Check if content area changed
    if (oldState.contentWidth !== this.state.contentWidth) {
      changedProperties.push('contentWidth');
    }
    if (oldState.contentHeight !== this.state.contentHeight) {
      changedProperties.push('contentHeight');
    }
    
    // Create change event
    const event: LayoutChangeEvent = {
      state: { ...this.state },
      changedProperties,
      reason,
      requestedBy,
    };
    
    // Notify listeners
    this.listeners.forEach(listener => {
      try {
        listener(event);
      } catch (error) {
        console.error('[LayoutStore] Listener error:', error);
      }
    });
  }
  
  /**
   * Recalculate content area dimensions
   */
  private recalculateContentArea(): void {
    let sidebarWidth = 0;
    
    if (this.state.isSidebarVisible) {
      sidebarWidth = this.state.isSidebarCollapsed
        ? (this.config.sidebarCollapsedWidth ?? 80)
        : (this.config.sidebarWidth ?? 256);
    }
    
    const headerHeight = this.state.isHeaderVisible 
      ? (this.config.headerHeight ?? 64) 
      : 0;
    const footerHeight = this.state.isFooterVisible 
      ? (this.config.footerHeight ?? 0) 
      : 0;
    
    this.state.contentWidth = this.state.viewportWidth - sidebarWidth;
    this.state.contentHeight = this.state.viewportHeight - headerHeight - footerHeight;
  }
  
  /**
   * Subscribe to state changes
   * 
   * @param handler - Change handler
   * @returns Unsubscribe function
   */
  subscribe(handler: LayoutChangeHandler): Unsubscribe {
    this.listeners.add(handler);
    
    return () => {
      this.listeners.delete(handler);
    };
  }
  
  /**
   * Destroy store
   */
  destroy(): void {
    this.listeners.clear();
    
    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
      this.resizeObserver = null;
    }
    
    if (typeof window !== 'undefined') {
      window.removeEventListener('resize', this.handleResize);
    }
  }
}
