/**
 * @file Responsive Hook
 * @description Provides responsive breakpoint detection for Runtime SDK
 * @module @brix/runtime-sdk-react/hooks/useResponsive
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
 *
 * 【响应式Hook】
 * 提供响应式断点检测的React Hook，包括：
 * - 当前断点（xs/sm/md/lg/xl/xxl）
 * - 设备类型检测（移动端/平板/桌面）
 * - 视口尺寸
 */

import { useState, useEffect, useMemo } from 'react';
import type { LayoutCapability, LayoutState } from '@brix/runtime-sdk-api-web';

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
   * 内容区域宽度（可选）
   */
  contentWidth?: number;
  
  /**
   * Content area height in pixels (optional)
   * 内容区域高度（可选）
   */
  contentHeight?: number;
  
  /**
   * Whether at extra-small breakpoint (<576px)
   */
  isXs: boolean;
  
  /**
   * Whether at small breakpoint (≥576px)
   */
  isSm: boolean;
  
  /**
   * Whether at medium breakpoint (≥768px)
   */
  isMd: boolean;
  
  /**
   * Whether at large breakpoint (≥992px)
   */
  isLg: boolean;
  
  /**
   * Whether at extra-large breakpoint (≥1200px)
   */
  isXl: boolean;
  
  /**
   * Whether at extra-extra-large breakpoint (≥1400px)
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
  
  // Subscribe to layout state changes
  // 订阅布局状态变化，在断点变化时更新（如果能力支持）
  useEffect(() => {
    // onLayoutChange is optional, check if available
    if (!layout.onLayoutChange) {
      return;
    }
    
    const unsubscribe = layout.onLayoutChange((event) => {
      // Update on breakpoint changes (most relevant for responsive)
      // 断点变化时更新（对响应式最相关）
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
  // 计算断点标志位
  const breakpointFlags = useMemo(() => ({
    isXs: state.breakpoint === 'xs',
    isSm: state.breakpoint === 'sm',
    isMd: state.breakpoint === 'md',
    isLg: state.breakpoint === 'lg',
    isXl: state.breakpoint === 'xl',
    isXxl: state.breakpoint === 'xxl',
  }), [state.breakpoint]);
  
  // Compute device type flags
  // 计算设备类型标志位
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
