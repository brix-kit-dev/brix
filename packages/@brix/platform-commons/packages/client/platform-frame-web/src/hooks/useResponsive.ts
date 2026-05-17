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
 * @description Provides responsive breakpoint detection
 * @module @brix-sdk/platform-frame-web/hooks/useResponsive
 * @version 3.0.0
 */

import { useState, useEffect, useMemo } from 'react';
import type { LayoutCapability, LayoutState } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Responsive Hook Return Type
 */
export interface UseResponsiveResult {
  /**
   * Current breakpoint
   */
  breakpoint: LayoutState['breakpoint'];
  
  /**
   * Viewport width
   */
  viewportWidth: number;
  
  /**
   * Viewport height
   */
  viewportHeight: number;
  
  /**
   * Whether at xs breakpoint
   */
  isXs: boolean;
  
  /**
   * Whether at sm breakpoint
   */
  isSm: boolean;
  
  /**
   * Whether at md breakpoint
   */
  isMd: boolean;
  
  /**
   * Whether at lg breakpoint
   */
  isLg: boolean;
  
  /**
   * Whether at xl breakpoint
   */
  isXl: boolean;
  
  /**
   * Whether at xxl breakpoint
   */
  isXxl: boolean;
  
  /**
   * Whether on mobile (xs or sm)
   */
  isMobile: boolean;
  
  /**
   * Whether on tablet (md or lg)
   */
  isTablet: boolean;
  
  /**
   * Whether on desktop (xl or xxl)
   */
  isDesktop: boolean;
}

/**
 * Responsive Hook
 * 
 * React Hook that provides responsive breakpoint detection.
 * 
 * [Usage Example]
 * ```tsx
 * function MyComponent() {
 *   const { isMobile, isTablet, isDesktop } = useResponsive(layoutCapability);
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
 * @returns Responsive state
 */
export function useResponsive(layout: LayoutCapability): UseResponsiveResult {
  const [state, setState] = useState(() => {
    const layoutState = layout.getState();
    return {
      breakpoint: layoutState.breakpoint,
    };
  });
  
  // Subscribe to layout state changes
  useEffect(() => {
    const unsubscribe = layout.onLayoutChange?.((event) => {
      if (event.type === 'breakpoint') {
        setState({
          breakpoint: event.state.breakpoint,
        });
      }
    });
    
    return () => unsubscribe?.();
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
  
  // Compute device type
  const deviceFlags = useMemo(() => ({
    isMobile: state.breakpoint === 'xs' || state.breakpoint === 'sm',
    isTablet: state.breakpoint === 'md' || state.breakpoint === 'lg',
    isDesktop: state.breakpoint === 'xl' || state.breakpoint === 'xxl',
  }), [state.breakpoint]);
  
  return {
    breakpoint: state.breakpoint,
    viewportWidth: state.viewportWidth,
    viewportHeight: state.viewportHeight,
    ...breakpointFlags,
    ...deviceFlags,
  };
}
