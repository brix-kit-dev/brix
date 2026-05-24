/**
 * @file Portal Layout Component
 * @description Portal-style layout for end users
 * @module @brix-sdk/platform-frame-web/layouts/PortalLayout
 * @version 3.0.0
 * 
 * [Design Notes]
 * PortalLayout provides a typical portal website layout:
 * - Top Header (Logo, navigation, user)
 * - Main content area (full-width or centered)
 * - Bottom Footer (copyright, links)
 */

import { 
  type ReactNode, 
  type CSSProperties,
  useMemo,
} from 'react';
import type { LayoutState } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Portal Layout Props
 */
export interface PortalLayoutProps {
  /**
   * Layout state
   */
  layoutState: LayoutState;
  
  /**
   * Header content
   */
  header?: ReactNode;
  
  /**
   * Main content area
   */
  children: ReactNode;
  
  /**
   * Footer content
   */
  footer?: ReactNode;
  
  /**
   * Header height
   * @default 64
   */
  headerHeight?: number;
  
  /**
   * Footer height
   * @default 200
   */
  footerHeight?: number;
  
  /**
   * Content max width
   * @default 1200
   */
  maxContentWidth?: number;
  
  /**
   * Whether to center content
   * @default true
   */
  centerContent?: boolean;
  
  /**
   * Custom style
   */
  style?: CSSProperties;
  
  /**
   * Custom class name
   */
  className?: string;
}

/**
 * Portal Layout Component
 * 
 * Provides typical portal website layout structure.
 * 
 * [Layout Structure]
 * ```
 * +----------------------------------------+
 * |            Header (64px)               |
 * +----------------------------------------+
 * |                                        |
 * |         Content (centered)             |
 * |        max-width: 1200px               |
 * |                                        |
 * |                                        |
 * +----------------------------------------+
 * |            Footer (200px)              |
 * +----------------------------------------+
 * ```
 * 
 * [Usage Example]
 * ```tsx
 * function PortalApp() {
 *   const layoutState = layoutCapability.getLayoutState();
 *   
 *   return (
 *     <PortalLayout
 *       layoutState={layoutState}
 *       header={<PortalHeader />}
 *       footer={<PortalFooter />}
 *       maxContentWidth={1440}
 *     >
 *       <Outlet />
 *     </PortalLayout>
 *   );
 * }
 * ```
 */
export function PortalLayout({
  layoutState,
  header,
  children,
  footer,
  headerHeight = 64,
  footerHeight = 200,
  maxContentWidth = 1200,
  centerContent = true,
  style,
  className,
}: PortalLayoutProps): ReactNode {
  // Container style
  const containerStyle = useMemo<CSSProperties>(() => ({
    display: 'flex',
    flexDirection: 'column',
    minHeight: '100vh',
    backgroundColor: 'var(--brix-bg-layout, #fff)',
    ...style,
  }), [style]);
  
  // Header style
  const headerStyle = useMemo<CSSProperties>(() => ({
    position: 'sticky',
    top: 0,
    height: `${headerHeight}px`,
    zIndex: 100,
    display: layoutState.headerVisible ? 'block' : 'none',
    backgroundColor: 'var(--brix-bg-header, #fff)',
    boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)',
  }), [headerHeight, layoutState.headerVisible]);
  
  // Header content container style (centered)
  const headerContentStyle = useMemo<CSSProperties>(() => ({
    maxWidth: centerContent ? `${maxContentWidth}px` : '100%',
    margin: '0 auto',
    height: '100%',
    padding: '0 24px',
  }), [centerContent, maxContentWidth]);
  
  // Main content area style
  const mainStyle = useMemo<CSSProperties>(() => ({
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
  }), []);
  
  // Content container style (centered)
  const contentContainerStyle = useMemo<CSSProperties>(() => ({
    maxWidth: centerContent ? `${maxContentWidth}px` : '100%',
    width: '100%',
    margin: '0 auto',
    padding: '24px',
    flex: 1,
  }), [centerContent, maxContentWidth]);
  
  // Footer style
  const footerStyle = useMemo<CSSProperties>(() => ({
    minHeight: `${footerHeight}px`,
    display: layoutState.footerVisible ? 'block' : 'none',
    backgroundColor: 'var(--brix-bg-footer, #001529)',
    color: 'var(--brix-text-footer, rgba(255, 255, 255, 0.65))',
  }), [footerHeight, layoutState.footerVisible]);
  
  // Footer content container style (centered)
  const footerContentStyle = useMemo<CSSProperties>(() => ({
    maxWidth: centerContent ? `${maxContentWidth}px` : '100%',
    margin: '0 auto',
    padding: '48px 24px',
  }), [centerContent, maxContentWidth]);
  
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
          <div style={headerContentStyle}>
            {header}
          </div>
        </header>
      )}
      
      {/* Main content area */}
      <main style={mainStyle}>
        <div style={contentContainerStyle}>
          {children}
        </div>
      </main>
      
      {/* Footer */}
      {footer && (
        <footer style={footerStyle}>
          <div style={footerContentStyle}>
            {footer}
          </div>
        </footer>
      )}
    </div>
  );
}
