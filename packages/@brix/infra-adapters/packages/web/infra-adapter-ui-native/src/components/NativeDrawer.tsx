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
 * @file Native Drawer Component
 * @description Pure CSS implementation of DrawerProps from UIAdapter contract.
 *              Slide-in panel from any edge of the viewport.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeDrawer
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Four placement options: left, right, top, bottom
 * - Modal backdrop with configurable mask
 * - Header with title and close button
 * - Optional footer slot
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic container component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for side panel dialogs.
 * Replaces direct MUI Drawer usage in enterprise-solutions plugins.
 */

import type { FC, CSSProperties } from 'react';
import { useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import type { DrawerProps, DrawerPlacement } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Placement Configurations
// ============================================================================

/**
 * Placement Transform Values
 *
 * <p>CSS transform values for off-screen positioning.</p>
 */
const PLACEMENT_TRANSFORMS: Record<DrawerPlacement, { closed: string; open: string }> = {
  left: { closed: 'translateX(-100%)', open: 'translateX(0)' },
  right: { closed: 'translateX(100%)', open: 'translateX(0)' },
  top: { closed: 'translateY(-100%)', open: 'translateY(0)' },
  bottom: { closed: 'translateY(100%)', open: 'translateY(0)' },
};

// ============================================================================
// Drawer Component
// ============================================================================

/**
 * Native Drawer Component
 *
 * <p>Pure CSS implementation of DrawerProps from UIAdapter contract.
 * Slide-in panel from any edge of the viewport.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS with transitions</li>
 *   <li>Four placements: left, right, top, bottom</li>
 *   <li>Configurable width/height based on placement</li>
 *   <li>Modal mask with optional click-to-close</li>
 *   <li>Header with title and close button</li>
 *   <li>Optional footer slot</li>
 *   <li>Body scroll lock when open</li>
 *   <li>Keyboard escape to close</li>
 * </ul>
 *
 * <h3>Architectural Constraints:</h3>
 * <ul>
 *   <li>This component is an atomic building block</li>
 *   <li>Shell layer uses this via UIAdapter interface</li>
 *   <li>No direct import allowed in Plugin layer</li>
 * </ul>
 *
 * @example
 * ```tsx
 * const { Drawer, Button } = useUI();
 *
 * <Drawer
 *   open={drawerOpen}
 *   onClose={() => setDrawerOpen(false)}
 *   title="Settings"
 *   placement="right"
 *   width={400}
 *   footer={
 *     <Button onClick={() => setDrawerOpen(false)}>Close</Button>
 *   }
 * >
 *   <SettingsForm />
 * </Drawer>
 * ```
 *
 * @param props - DrawerProps from UIAdapter contract
 * @returns Native Drawer component (rendered via portal)
 */
export const NativeDrawer: FC<DrawerProps> = ({
  open = false,
  onClose,
  title,
  placement = 'right',
  width = 378,
  height = 378,
  closable = true,
  mask = true,
  maskClosable = true,
  keyboard = true,
  destroyOnClose = false,
  zIndex = 1000,
  getContainer,
  headerStyle,
  bodyStyle,
  footerStyle,
  extra,
  footer,
  afterOpenChange,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Handle escape key
  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (keyboard && e.key === 'Escape' && open) {
        onClose?.();
      }
    },
    [keyboard, open, onClose]
  );

  // Lock body scroll when open
  useEffect(() => {
    if (open) {
      const originalOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';

      document.addEventListener('keydown', handleKeyDown);

      return () => {
        document.body.style.overflow = originalOverflow;
        document.removeEventListener('keydown', handleKeyDown);
      };
    }
  }, [open, handleKeyDown]);

  // Call afterOpenChange callback
  useEffect(() => {
    afterOpenChange?.(open);
  }, [open, afterOpenChange]);

  // If destroyOnClose and not open, don't render
  if (destroyOnClose && !open) {
    return null;
  }

  // Determine drawer size based on placement
  const isHorizontal = placement === 'left' || placement === 'right';

  // Wrapper styles (overlay container)
  const wrapperStyle: CSSProperties = {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    zIndex,
    pointerEvents: open ? 'auto' : 'none',
    visibility: open ? 'visible' : 'hidden',
  };

  // Mask styles
  const maskStyle: CSSProperties = {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.45)',
    opacity: open ? 1 : 0,
    transition: 'opacity 0.3s ease',
  };

  // Drawer panel styles
  const drawerStyle: CSSProperties = {
    position: 'absolute',
    [placement]: 0,
    top: isHorizontal ? 0 : placement === 'top' ? 0 : 'auto',
    bottom: isHorizontal ? 0 : placement === 'bottom' ? 0 : 'auto',
    left: !isHorizontal ? 0 : placement === 'left' ? 0 : 'auto',
    right: !isHorizontal ? 0 : placement === 'right' ? 0 : 'auto',
    width: isHorizontal ? width : '100%',
    height: !isHorizontal ? height : '100%',
    backgroundColor: '#fff',
    boxShadow: '-6px 0 16px -8px rgba(0,0,0,0.08), -9px 0 28px 0 rgba(0,0,0,0.05), -12px 0 48px 16px rgba(0,0,0,0.03)',
    display: 'flex',
    flexDirection: 'column',
    transform: open ? PLACEMENT_TRANSFORMS[placement].open : PLACEMENT_TRANSFORMS[placement].closed,
    transition: 'transform 0.3s cubic-bezier(0.7, 0.3, 0.1, 1)',
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    ...style,
  };

  // Header styles
  const headerStyles: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '16px 24px',
    borderBottom: '1px solid #e0e0e0',
    flexShrink: 0,
    ...headerStyle,
  };

  // Title styles
  const titleStyle: CSSProperties = {
    fontSize: '16px',
    fontWeight: 500,
    color: 'rgba(0, 0, 0, 0.87)',
    margin: 0,
    flex: 1,
  };

  // Close button styles
  const closeButtonStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: 28,
    height: 28,
    padding: 0,
    border: 'none',
    backgroundColor: 'transparent',
    borderRadius: 4,
    cursor: 'pointer',
    color: 'rgba(0, 0, 0, 0.45)',
    fontSize: '16px',
    transition: 'background-color 0.2s, color 0.2s',
    marginLeft: 8,
  };

  // Body styles
  const bodyStyles: CSSProperties = {
    flex: 1,
    padding: 24,
    overflow: 'auto',
    ...bodyStyle,
  };

  // Footer styles
  const footerStyles: CSSProperties = {
    padding: '16px 24px',
    borderTop: '1px solid #e0e0e0',
    flexShrink: 0,
    ...footerStyle,
  };

  // Handle mask click
  const handleMaskClick = () => {
    if (maskClosable) {
      onClose?.();
    }
  };

  // Render drawer content
  const drawerContent = (
    <div
      style={wrapperStyle}
      className={className}
      data-testid={dataTestId}
      data-placement={placement}
      aria-hidden={!open}
    >
      {/* Mask */}
      {mask && <div style={maskStyle} onClick={handleMaskClick} />}

      {/* Drawer panel */}
      <div
        style={drawerStyle}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? 'drawer-title' : undefined}
      >
        {/* Header */}
        {(title || closable || extra) && (
          <div style={headerStyles}>
            {title && (
              <h3 id="drawer-title" style={titleStyle}>
                {title}
              </h3>
            )}
            {extra && <div style={{ marginRight: closable ? 8 : 0 }}>{extra}</div>}
            {closable && (
              <button
                type="button"
                style={closeButtonStyle}
                onClick={() => onClose?.()}
                aria-label="Close drawer"
                onMouseEnter={(e) => {
                  e.currentTarget.style.backgroundColor = 'rgba(0, 0, 0, 0.04)';
                  e.currentTarget.style.color = 'rgba(0, 0, 0, 0.87)';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.backgroundColor = 'transparent';
                  e.currentTarget.style.color = 'rgba(0, 0, 0, 0.45)';
                }}
              >
                ✕
              </button>
            )}
          </div>
        )}

        {/* Body */}
        <div style={bodyStyles}>{children}</div>

        {/* Footer */}
        {footer && <div style={footerStyles}>{footer}</div>}
      </div>
    </div>
  );

  // Render in portal
  const container = getContainer?.() || document.body;
  return createPortal(drawerContent, container);
};

NativeDrawer.displayName = 'NativeDrawer';

export default NativeDrawer;
