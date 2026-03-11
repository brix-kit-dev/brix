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
 * @description Provides layout-related React Hooks for Runtime SDK
 * @module @brix/runtime-sdk-react/hooks/useLayout
 * @version 3.2.0
 *
 * [Architecture Positioning]
 * This hook provides React bindings for LayoutCapability,
 * enabling layout state management in React components.
 *
 * [Design Principles]
 * - Reactive layout state updates
 * - Fullscreen and sidebar control
 * - Breakpoint-aware rendering
 *
 * 【布局Hook】
 * 提供布局状态和控制方法的React Hook，包括：
 * - 全屏控制
 * - 侧边栏显示/折叠控制
 * - 断点检测
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import type { LayoutCapability, LayoutState, LayoutChangeEvent } from '@brix/runtime-sdk-api-web';

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
 * React Hook that provides layout state and control methods.
 * Subscribes to layout changes and updates components reactively.
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const layout = useRuntimeContext().getCapability('layout');
 *   const {
 *     isMobile,
 *     isSidebarCollapsed,
 *     toggleSidebarCollapsed,
 *     toggleFullscreen,
 *   } = useLayout(layout);
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
 * @param layout - Layout capability instance
 * @returns Layout state and control methods
 */
export function useLayout(layout: LayoutCapability): UseLayoutResult {
  const [layoutState, setLayoutState] = useState<LayoutState>(() => layout.getState());
  
  // Subscribe to layout state changes
  // 订阅布局状态变化事件（如果能力支持）
  useEffect(() => {
    // onLayoutChange is optional, check if available
    if (!layout.onLayoutChange) {
      return;
    }
    
    const unsubscribe = layout.onLayoutChange((event: LayoutChangeEvent) => {
      setLayoutState(event.state);
    });
    
    return () => unsubscribe();
  }, [layout]);
  
  // Request fullscreen mode
  // 请求进入全屏模式
  const requestFullscreen = useCallback(
    () => layout.requestFullscreen(),
    [layout]
  );
  
  // Exit fullscreen mode
  // 请求退出全屏模式
  const requestExitFullscreen = useCallback(
    () => layout.requestExitFullscreen(),
    [layout]
  );
  
  // Toggle fullscreen mode
  const toggleFullscreen = useCallback(async () => {
    if (layoutState.fullscreen) {
      return layout.requestExitFullscreen();
    } else {
      return layout.requestFullscreen();
    }
  }, [layout, layoutState.fullscreen]);
  
  // Toggle sidebar visibility
  const toggleSidebar = useCallback(async () => {
    if (layoutState.sidebarVisible) {
      return layout.requestHideSidebar();
    } else {
      return layout.requestShowSidebar();
    }
  }, [layout, layoutState.sidebarVisible]);
  
  // Toggle sidebar collapsed state
  // 切换侧边栏折叠状态
  const toggleSidebarCollapsed = useCallback(async () => {
    if (layoutState.sidebarCollapsed) {
      // requestExpandSidebar is optional, check if available
      return layout.requestExpandSidebar?.() ?? Promise.resolve(false);
    } else {
      // requestCollapseSidebar is optional, check if available
      return layout.requestCollapseSidebar?.() ?? Promise.resolve(false);
    }
  }, [layout, layoutState.sidebarCollapsed]);
  
  // Compute mobile flag
  // 计算是否为移动设备
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
