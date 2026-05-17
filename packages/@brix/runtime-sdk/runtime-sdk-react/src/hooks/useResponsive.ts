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
 * @file Responsive Hook
 * @description Provides responsive breakpoint detection for Runtime SDK
 * @module @brix-sdk/runtime-sdk-react/hooks/useResponsive
 * @version 3.2.0
 *
 * [Architecture Positioning]
 * This hook provides responsive breakpoint detection based on LayoutCapability,
 * enabling breakpoint-aware rendering in React components.
 *
 * [Design Principles]
 * - Reactive breakpoint updates
 * - Device-type detection (mobile/tablet/desktop)
 * - Minimal re-renders via selective subscription
 */

import { useState, useEffect, useMemo } from 'react';
import type { LayoutCapability, LayoutState } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Responsive Hook Return Type
 *
 * Defines the shape of the value returned by useResponsive hook.
 */
export interface UseResponsiveResult {
  /**
   * Current responsive breakpoint
   */
  breakpoint: LayoutState['breakpoint'];
  
  /**
   * Content area width in pixels (optional)
   */
  contentWidth?: number;
  
  /**
   * Content area height in pixels (optional)
   */
  contentHeight?: number;
  
  /**
   * Whether at extra-small breakpoint (<576px)
   */
  isXs: boolean;
  
  /**
   * Whether at small breakpoint (¡Ý576px)
   */
  isSm: boolean;
  
  /**
   * Whether at medium breakpoint (¡Ý768px)
   */
  isMd: boolean;
  
  /**
   * Whether at large breakpoint (¡Ý992px)
   */
  isLg: boolean;
  
  /**
   * Whether at extra-large breakpoint (¡Ý1200px)
   */
  isXl: boolean;
  
  /**
   * Whether at extra-extra-large breakpoint (¡Ý1400px)
   */
  isXxl: boolean;
  
  /**
   * Whether on mobile device (xs or sm)
   */
  isMobile: boolean;
  
  /**
   * Whether on tablet device (md or lg)
   */
  isTablet: boolean;
  
  /**
   * Whether on desktop device (xl or xxl)
   */
  isDesktop: boolean;
}

/**
 * Responsive Hook
 *
 * React Hook that provides responsive breakpoint detection.
 * Subscribes to layout changes and updates components reactively when
 * breakpoint or viewport size changes.
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const layout = useRuntimeContext().getCapability('layout');
 *   const { isMobile, isTablet, isDesktop } = useResponsive(layout);
 *   
 *   if (isMobile) {
 *     return <MobileView />;
 *   }
 *   
 *   if (isTablet) {
 *     return <TabletView />;
 *   }
 *   
 *   return <DesktopView />;
 * }
 * ```
 *
 * @param layout - Layout capability instance
 * @returns Responsive state with breakpoint flags
 */
export function useResponsive(layout: LayoutCapability): UseResponsiveResult {
  const [state, setState] = useState(() => {
    const layoutState = layout.getState();
    return {
      breakpoint: layoutState.breakpoint,
      contentWidth: layoutState.contentWidth,
      contentHeight: layoutState.contentHeight,
    };
  });
  
  // Subscribe to layout state changes and update on breakpoint changes (if capability supports it)
  useEffect(() => {
    // onLayoutChange is optional, check if available
    if (!layout.onLayoutChange) {
      return;
    }
    
    const unsubscribe = layout.onLayoutChange((event) => {
      // Update on breakpoint changes (most relevant for responsive)
      if (event.type === 'breakpoint') {
        setState({
          breakpoint: event.state.breakpoint,
          contentWidth: event.state.contentWidth,
          contentHeight: event.state.contentHeight,
        });
      }
    });
    
    return () => unsubscribe();
  }, [layout]);
  
  // Compute breakpoint flags
  const breakpointFlags = useMemo(() => ({
    isXs: state.breakpoint === 'xs',
    isSm: state.breakpoint === 'sm',
    isMd: state.breakpoint === 'md',
    isLg: state.breakpoint === 'lg',
    isXl: state.breakpoint === 'xl',
    isXxl: state.breakpoint === 'xxl',
  }), [state.breakpoint]);
  
  // Compute device type flags
  const deviceFlags = useMemo(() => ({
    isMobile: state.breakpoint === 'xs' || state.breakpoint === 'sm',
    isTablet: state.breakpoint === 'md' || state.breakpoint === 'lg',
    isDesktop: state.breakpoint === 'xl' || state.breakpoint === 'xxl',
  }), [state.breakpoint]);
  
  return {
    breakpoint: state.breakpoint,
    contentWidth: state.contentWidth,
    contentHeight: state.contentHeight,
    ...breakpointFlags,
    ...deviceFlags,
  };
}
