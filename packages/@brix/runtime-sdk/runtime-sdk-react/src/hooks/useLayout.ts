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
 * @file Layout Hook
 * @description Provides layout-related React Hooks for Runtime SDK.
 *              Resolves LayoutCapability from RuntimeContext following the
 *              standard capability hook pattern (useAuth, useI18n, useTenant).
 * @module @brix-sdk/runtime-sdk-react/hooks/useLayout
 * @version 3.2.1
 *
 * [Architecture Positioning]
 * React binding layer — bridges LayoutCapability contract to React components.
 * Plugins access layout state and controls exclusively through this hook.
 *
 * [Architecture Compliance]
 * - Blueprint v3.0.9 Constraint 2: Plugins only depend on Capability Contract
 * - Phase 2.1: Formal useLayout hook resolving from RuntimeContext
 *
 * [Migration Guide]
 * Before (v3.2.0 — required manual capability parameter):
 *   const layout = useRuntimeContext().getCapability<LayoutCapability>(...);
 *   const { isMobile } = useLayout(layout);
 *
 * After (v3.2.1 — resolves automatically from RuntimeContext):
 *   const { isMobile, toggleSidebar } = useLayout();
 *
 * @since 3.2.0
 * @see LayoutCapability — Contract in runtime-sdk-api-web
 * @see LayoutCapabilityImpl — Implementation in platform-frame-web
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import type { LayoutCapability, LayoutState, LayoutChangeEvent } from '@brix-sdk/runtime-sdk-api-web';
import { useRuntimeContext } from './useRuntimeContext';

/**
 * LayoutCapability type identifier.
 * Matches the Symbol used in bootstrap registration.
 * @internal
 */
const LayoutCapabilityType = Symbol.for('LayoutCapability');

/**
 * Layout Hook Return Type
 *
 * Defines the shape of the value returned by useLayout hook.
 */
export interface UseLayoutResult {
  /**
   * Current layout state
   */
  layoutState: LayoutState;
  
  /**
   * Whether currently in fullscreen mode
   */
  isFullscreen: boolean;
  
  /**
   * Whether sidebar is visible
   */
  isSidebarVisible: boolean;
  
  /**
   * Whether sidebar is collapsed
   */
  isSidebarCollapsed: boolean;
  
  /**
   * Current responsive breakpoint
   */
  breakpoint: LayoutState['breakpoint'];
  
  /**
   * Whether on mobile device (xs or sm breakpoint)
   */
  isMobile: boolean;
  
  /**
   * Request fullscreen mode
   * @returns Promise resolving to success status
   */
  requestFullscreen: () => Promise<boolean>;
  
  /**
   * Exit fullscreen mode
   * @returns Promise resolving to success status
   */
  requestExitFullscreen: () => Promise<boolean>;
  
  /**
   * Toggle fullscreen mode
   * @returns Promise resolving to success status
   */
  toggleFullscreen: () => Promise<boolean>;
  
  /**
   * Toggle sidebar visibility
   * @returns Promise resolving to success status
   */
  toggleSidebar: () => Promise<boolean>;
  
  /**
   * Toggle sidebar collapsed state
   * @returns Promise resolving to success status
   */
  toggleSidebarCollapsed: () => Promise<boolean>;
}

/**
 * Layout Hook
 *
 * Resolves LayoutCapability from RuntimeContext and provides reactive
 * layout state for React components. Automatically re-renders when
 * layout state changes (fullscreen, sidebar, breakpoint).
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const {
 *     isMobile,
 *     isSidebarCollapsed,
 *     toggleSidebarCollapsed,
 *     toggleFullscreen,
 *   } = useLayout();
 *
 *   return (
 *     <div>
 *       <button onClick={toggleSidebarCollapsed}>
 *         {isSidebarCollapsed ? 'Expand' : 'Collapse'}
 *       </button>
 *       <button onClick={toggleFullscreen}>Fullscreen</button>
 *     </div>
 *   );
 * }
 * ```
 *
 * @returns Layout state and control methods
 * @throws Error if used outside RuntimeContextProvider
 * @throws Error if LayoutCapability is not registered
 */
export function useLayout(): UseLayoutResult {
  const context = useRuntimeContext();

  // Resolve LayoutCapability from RuntimeContext (memoized per context instance)
  const layoutCapability = useMemo(() => {
    const capability = context.getCapability<LayoutCapability>(LayoutCapabilityType);
    if (!capability) {
      throw new Error(
        '[runtime-sdk-react] LayoutCapability is not registered in RuntimeContext. ' +
        'Ensure the Host registers LayoutCapability in bootstrap via ' +
        'runtime.registerCapability(LayoutCapabilityType, layoutCapability).'
      );
    }
    return capability;
  }, [context]);

  const [layoutState, setLayoutState] = useState<LayoutState>(() => layoutCapability.getState());
  
  // Subscribe to layout state changes
  useEffect(() => {
    if (!layoutCapability.onLayoutChange) {
      return;
    }

    const unsubscribe = layoutCapability.onLayoutChange((event: LayoutChangeEvent) => {
      setLayoutState(event.state);
    });

    return () => unsubscribe();
  }, [layoutCapability]);

  const requestFullscreen = useCallback(
    () => layoutCapability.requestFullscreen(),
    [layoutCapability]
  );

  const requestExitFullscreen = useCallback(
    () => layoutCapability.requestExitFullscreen(),
    [layoutCapability]
  );

  const toggleFullscreen = useCallback(async () => {
    if (layoutState.fullscreen) {
      return layoutCapability.requestExitFullscreen();
    } else {
      return layoutCapability.requestFullscreen();
    }
  }, [layoutCapability, layoutState.fullscreen]);

  const toggleSidebar = useCallback(async () => {
    if (layoutState.sidebarVisible) {
      return layoutCapability.requestHideSidebar();
    } else {
      return layoutCapability.requestShowSidebar();
    }
  }, [layoutCapability, layoutState.sidebarVisible]);

  const toggleSidebarCollapsed = useCallback(async () => {
    if (layoutState.sidebarCollapsed) {
      return layoutCapability.requestExpandSidebar?.() ?? Promise.resolve(false);
    } else {
      return layoutCapability.requestCollapseSidebar?.() ?? Promise.resolve(false);
    }
  }, [layoutCapability, layoutState.sidebarCollapsed]);
  
  // Compute mobile flag based on current breakpoint
  const isMobile = useMemo(() => {
    return layoutState.breakpoint === 'xs' || layoutState.breakpoint === 'sm';
  }, [layoutState.breakpoint]);
  
  return {
    layoutState,
    isFullscreen: layoutState.fullscreen,
    isSidebarVisible: layoutState.sidebarVisible,
    isSidebarCollapsed: layoutState.sidebarCollapsed,
    breakpoint: layoutState.breakpoint,
    isMobile,
    requestFullscreen,
    requestExitFullscreen,
    toggleFullscreen,
    toggleSidebar,
    toggleSidebarCollapsed,
  };
}
