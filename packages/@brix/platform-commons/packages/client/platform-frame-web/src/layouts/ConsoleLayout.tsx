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
 * @file Console Layout Component
 * @description Admin dashboard style layout
 * @module @brix-sdk/platform-frame-web/layouts/ConsoleLayout
 * @version 3.0.0
 * 
 * [Design Notes]
 * ConsoleLayout provides a typical admin system layout:
 * - Top Header (navigation, user info)
 * - Left Sidebar (menu)
 * - Right Content (plugin rendering area)
 */

import { 
  type ReactNode, 
  type CSSProperties,
  useMemo,
} from 'react';
import type { LayoutState } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Console Layout Props
 */
export interface ConsoleLayoutProps {
  /**
   * Layout state
   */
  layoutState: LayoutState;
  
  /**
   * Header content
   */
  header?: ReactNode;
  
  /**
   * Sidebar content
   */
  sidebar?: ReactNode;
  
  /**
   * Main content area
   */
  children: ReactNode;
  
  /**
   * Footer content
   */
  footer?: ReactNode;
  
  /**
   * Sidebar width (expanded state)
   * @default 256
   */
  sidebarWidth?: number;
  
  /**
   * Sidebar width (collapsed state)
   * @default 80
   */
  sidebarCollapsedWidth?: number;
  
  /**
   * Header height
   * @default 64
   */
  headerHeight?: number;
  
  /**
   * Footer height
   * @default 0
   */
  footerHeight?: number;
  
  /**
   * Custom style
   */
  style?: CSSProperties;
  
  /**
   * Custom class name
   */
  className?: string;
  
  /**
   * Toggle sidebar collapsed state
   */
  onSidebarCollapse?: (collapsed: boolean) => void;
}

/**
 * Console Layout Component
 * 
 * Provides typical admin system layout structure.
 * 
 * [Layout Structure]
 * ```
 * +----------------------------------------+
 * |            Header (64px)               |
 * +-----------+----------------------------+
 * |           |                            |
 * | Sidebar   |         Content            |
 * | (256px)   |                            |
 * |           |                            |
 * |           |                            |
 * +-----------+----------------------------+
 * |            Footer (optional)           |
 * +----------------------------------------+
 * ```
 * 
 * [Usage Example]
 * ```tsx
 * function App() {
 *   const layoutState = layoutCapability.getLayoutState();
 *   
 *   return (
 *     <ConsoleLayout
 *       layoutState={layoutState}
 *       header={<AppHeader />}
 *       sidebar={<AppMenu />}
 *     >
 *       <Outlet />
 *     </ConsoleLayout>
 *   );
 * }
 * ```
 */
export function ConsoleLayout({
  layoutState,
  header,
  sidebar,
  children,
  footer,
  sidebarWidth = 256,
  sidebarCollapsedWidth = 80,
  headerHeight = 64,
  footerHeight = 0,
  style,
  className,
  onSidebarCollapse: _onSidebarCollapse,
}: ConsoleLayoutProps): ReactNode {
  // Calculate actual sidebar width
  const actualSidebarWidth = useMemo(() => {
    if (!layoutState.sidebarVisible) return 0;
    return layoutState.sidebarCollapsed ? sidebarCollapsedWidth : sidebarWidth;
  }, [
    layoutState.sidebarVisible,
    layoutState.sidebarCollapsed,
    sidebarWidth,
    sidebarCollapsedWidth,
  ]);
  
  // Container style
  const containerStyle = useMemo<CSSProperties>(() => ({
    display: 'flex',
    flexDirection: 'column',
    minHeight: '100vh',
    backgroundColor: 'var(--brix-bg-layout, #f0f2f5)',
    ...style,
  }), [style]);
  
  // Header style
  const headerStyle = useMemo<CSSProperties>(() => ({
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    height: `${headerHeight}px`,
    zIndex: 100,
    display: layoutState.headerVisible ? 'block' : 'none',
    backgroundColor: 'var(--brix-bg-header, #001529)',
    boxShadow: '0 1px 4px rgba(0, 21, 41, 0.08)',
  }), [headerHeight, layoutState.headerVisible]);
  
  // Body area style
  const bodyStyle = useMemo<CSSProperties>(() => ({
    display: 'flex',
    flex: 1,
    marginTop: layoutState.headerVisible ? `${headerHeight}px` : 0,
    marginBottom: layoutState.footerVisible ? `${footerHeight}px` : 0,
  }), [
    layoutState.headerVisible,
    layoutState.footerVisible,
    headerHeight,
    footerHeight,
  ]);
  
  // Sidebar style
  const sidebarStyle = useMemo<CSSProperties>(() => ({
    position: 'fixed',
    top: layoutState.headerVisible ? `${headerHeight}px` : 0,
    left: 0,
    bottom: layoutState.footerVisible ? `${footerHeight}px` : 0,
    width: `${actualSidebarWidth}px`,
    backgroundColor: 'var(--brix-bg-sidebar, #001529)',
    overflow: 'auto',
    transition: 'width 0.2s ease-in-out',
    zIndex: 99,
    display: layoutState.sidebarVisible ? 'block' : 'none',
  }), [
    layoutState.headerVisible,
    layoutState.footerVisible,
    layoutState.sidebarVisible,
    actualSidebarWidth,
    headerHeight,
    footerHeight,
  ]);
  
  // Content area style
  const contentStyle = useMemo<CSSProperties>(() => ({
    marginLeft: `${actualSidebarWidth}px`,
    flex: 1,
    minHeight: 0,
    padding: '24px',
    backgroundColor: 'var(--brix-bg-content, #fff)',
    transition: 'margin-left 0.2s ease-in-out',
    overflow: 'auto',
  }), [actualSidebarWidth]);
  
  // Footer style
  const footerStyle = useMemo<CSSProperties>(() => ({
    position: 'fixed',
    bottom: 0,
    left: 0,
    right: 0,
    height: `${footerHeight}px`,
    zIndex: 100,
    display: layoutState.footerVisible && footerHeight > 0 ? 'block' : 'none',
    backgroundColor: 'var(--brix-bg-footer, #fff)',
    borderTop: '1px solid var(--brix-border-color, #f0f0f0)',
  }), [footerHeight, layoutState.footerVisible]);
  
  // Special handling for fullscreen mode
  const isFullscreen = layoutState.fullscreen;
  
  if (isFullscreen) {
    // Fullscreen mode only shows content area
    return (
      <div 
        className={className}
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'var(--brix-bg-content, #fff)',
          overflow: 'auto',
          zIndex: 1000,
        }}
      >
        {children}
      </div>
    );
  }
  
  return (
    <div className={className} style={containerStyle}>
      {/* Header */}
      {header && (
        <header style={headerStyle}>
          {header}
        </header>
      )}
      
      {/* Body area */}
      <div style={bodyStyle}>
        {/* Sidebar */}
        {sidebar && (
          <aside style={sidebarStyle}>
            {sidebar}
          </aside>
        )}
        
        {/* Content area */}
        <main style={contentStyle}>
          {children}
        </main>
      </div>
      
      {/* Footer */}
      {footer && footerHeight > 0 && (
        <footer style={footerStyle}>
          {footer}
        </footer>
      )}
    </div>
  );
}
