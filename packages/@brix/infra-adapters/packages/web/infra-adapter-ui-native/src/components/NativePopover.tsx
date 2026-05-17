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
 * @file Native Popover Component
 * @description Pure CSS implementation of PopoverProps from UIAdapter contract.
 *              Floating content panel triggered by hover or click.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativePopover
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - 12 placement options with arrow indicator
 * - Trigger modes: hover, click, focus
 * - Optional title and configurable content
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic container component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for contextual information.
 * Replaces direct MUI Popover usage in enterprise-solutions plugins.
 */

import type { FC, CSSProperties, ReactNode, ReactElement } from 'react';
import { useState, useCallback, useRef, useEffect, cloneElement, isValidElement } from 'react';
import { createPortal } from 'react-dom';
import type { PopoverProps, PopoverPlacement, TooltipTrigger } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Placement Configurations
// ============================================================================

/**
 * Placement Position Calculations
 *
 * <p>Functions to calculate popover position based on trigger element.</p>
 */
const calculatePosition = (
  placement: PopoverPlacement,
  triggerRect: DOMRect,
  popoverRect: DOMRect,
  arrowSize: number = 8
): { top: number; left: number; arrowStyle: CSSProperties } => {
  const gap = arrowSize + 4; // Arrow size + gap

  let top = 0;
  let left = 0;
  const arrowStyle: CSSProperties = { position: 'absolute' };

  switch (placement) {
    case 'top':
      top = triggerRect.top - popoverRect.height - gap;
      left = triggerRect.left + (triggerRect.width - popoverRect.width) / 2;
      arrowStyle.bottom = -arrowSize;
      arrowStyle.left = '50%';
      arrowStyle.transform = 'translateX(-50%) rotate(45deg)';
      break;
    case 'topLeft':
      top = triggerRect.top - popoverRect.height - gap;
      left = triggerRect.left;
      arrowStyle.bottom = -arrowSize;
      arrowStyle.left = 16;
      arrowStyle.transform = 'rotate(45deg)';
      break;
    case 'topRight':
      top = triggerRect.top - popoverRect.height - gap;
      left = triggerRect.right - popoverRect.width;
      arrowStyle.bottom = -arrowSize;
      arrowStyle.right = 16;
      arrowStyle.transform = 'rotate(45deg)';
      break;
    case 'bottom':
      top = triggerRect.bottom + gap;
      left = triggerRect.left + (triggerRect.width - popoverRect.width) / 2;
      arrowStyle.top = -arrowSize;
      arrowStyle.left = '50%';
      arrowStyle.transform = 'translateX(-50%) rotate(45deg)';
      break;
    case 'bottomLeft':
      top = triggerRect.bottom + gap;
      left = triggerRect.left;
      arrowStyle.top = -arrowSize;
      arrowStyle.left = 16;
      arrowStyle.transform = 'rotate(45deg)';
      break;
    case 'bottomRight':
      top = triggerRect.bottom + gap;
      left = triggerRect.right - popoverRect.width;
      arrowStyle.top = -arrowSize;
      arrowStyle.right = 16;
      arrowStyle.transform = 'rotate(45deg)';
      break;
    case 'left':
      top = triggerRect.top + (triggerRect.height - popoverRect.height) / 2;
      left = triggerRect.left - popoverRect.width - gap;
      arrowStyle.right = -arrowSize;
      arrowStyle.top = '50%';
      arrowStyle.transform = 'translateY(-50%) rotate(45deg)';
      break;
    case 'leftTop':
      top = triggerRect.top;
      left = triggerRect.left - popoverRect.width - gap;
      arrowStyle.right = -arrowSize;
      arrowStyle.top = 12;
      arrowStyle.transform = 'rotate(45deg)';
      break;
    case 'leftBottom':
      top = triggerRect.bottom - popoverRect.height;
      left = triggerRect.left - popoverRect.width - gap;
      arrowStyle.right = -arrowSize;
      arrowStyle.bottom = 12;
      arrowStyle.transform = 'rotate(45deg)';
      break;
    case 'right':
      top = triggerRect.top + (triggerRect.height - popoverRect.height) / 2;
      left = triggerRect.right + gap;
      arrowStyle.left = -arrowSize;
      arrowStyle.top = '50%';
      arrowStyle.transform = 'translateY(-50%) rotate(45deg)';
      break;
    case 'rightTop':
      top = triggerRect.top;
      left = triggerRect.right + gap;
      arrowStyle.left = -arrowSize;
      arrowStyle.top = 12;
      arrowStyle.transform = 'rotate(45deg)';
      break;
    case 'rightBottom':
      top = triggerRect.bottom - popoverRect.height;
      left = triggerRect.right + gap;
      arrowStyle.left = -arrowSize;
      arrowStyle.bottom = 12;
      arrowStyle.transform = 'rotate(45deg)';
      break;
  }

  // Ensure popover stays within viewport
  const padding = 8;
  top = Math.max(padding, Math.min(top, window.innerHeight - popoverRect.height - padding));
  left = Math.max(padding, Math.min(left, window.innerWidth - popoverRect.width - padding));

  return { top, left, arrowStyle };
};

// ============================================================================
// Popover Component
// ============================================================================

/**
 * Native Popover Component
 *
 * <p>Pure CSS implementation of PopoverProps from UIAdapter contract.
 * Floating panel with optional title and rich content.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS with portal</li>
 *   <li>12 placement options with arrow indicator</li>
 *   <li>Multiple triggers: hover, click, focus</li>
 *   <li>Optional title header</li>
 *   <li>Controlled and uncontrolled visibility</li>
 *   <li>Auto-positioning to stay in viewport</li>
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
 * const { Popover, Button } = useUI();
 *
 * <Popover
 *   title="User Info"
 *   content={
 *     <div>
 *       <p>Name: John Doe</p>
 *       <p>Email: john@example.com</p>
 *     </div>
 *   }
 *   trigger="hover"
 *   placement="right"
 * >
 *   <Button>Hover Me</Button>
 * </Popover>
 * ```
 *
 * @param props - PopoverProps from UIAdapter contract
 * @returns Native Popover component
 */
export const NativePopover: FC<PopoverProps> = ({
  open: controlledOpen,
  defaultOpen = false,
  title,
  content,
  placement = 'top',
  trigger = 'hover',
  arrow = true,
  mouseEnterDelay = 100,
  mouseLeaveDelay = 100,
  overlayClassName,
  overlayStyle,
  overlayInnerStyle,
  zIndex = 1030,
  getPopupContainer,
  destroyTooltipOnHide = false,
  onOpenChange,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Internal visibility state
  const [internalOpen, setInternalOpen] = useState(defaultOpen);
  const isOpen = controlledOpen ?? internalOpen;

  // Position state
  const [position, setPosition] = useState<{ top: number; left: number; arrowStyle: CSSProperties } | null>(null);

  // Refs
  const triggerRef = useRef<HTMLElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);
  const hoverTimeoutRef = useRef<number | null>(null);

  // Trigger handlers
  const triggers: TooltipTrigger[] = Array.isArray(trigger) ? trigger : [trigger];

  // Open popover
  const openPopover = useCallback(() => {
    if (controlledOpen === undefined) {
      setInternalOpen(true);
    }
    onOpenChange?.(true);
  }, [controlledOpen, onOpenChange]);

  // Close popover
  const closePopover = useCallback(() => {
    if (controlledOpen === undefined) {
      setInternalOpen(false);
    }
    onOpenChange?.(false);
  }, [controlledOpen, onOpenChange]);

  // Toggle popover (for click trigger)
  const togglePopover = useCallback(() => {
    if (isOpen) {
      closePopover();
    } else {
      openPopover();
    }
  }, [isOpen, openPopover, closePopover]);

  // Calculate position when open
  useEffect(() => {
    if (isOpen && triggerRef.current && popoverRef.current) {
      const triggerRect = triggerRef.current.getBoundingClientRect();
      const popoverRect = popoverRef.current.getBoundingClientRect();
      const pos = calculatePosition(placement, triggerRect, popoverRect);
      setPosition(pos);
    }
  }, [isOpen, placement]);

  // Handle click outside
  useEffect(() => {
    if (isOpen && triggers.includes('click')) {
      const handleClickOutside = (e: MouseEvent) => {
        const target = e.target as Node;
        if (
          !triggerRef.current?.contains(target) &&
          !popoverRef.current?.contains(target)
        ) {
          closePopover();
        }
      };

      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [isOpen, triggers, closePopover]);

  // Clear timeouts on unmount
  useEffect(() => {
    return () => {
      if (hoverTimeoutRef.current) {
        clearTimeout(hoverTimeoutRef.current);
      }
    };
  }, []);

  // Event handlers
  const handleMouseEnter = () => {
    if (!triggers.includes('hover')) return;

    if (hoverTimeoutRef.current) {
      clearTimeout(hoverTimeoutRef.current);
    }

    hoverTimeoutRef.current = window.setTimeout(() => {
      openPopover();
    }, mouseEnterDelay);
  };

  const handleMouseLeave = () => {
    if (!triggers.includes('hover')) return;

    if (hoverTimeoutRef.current) {
      clearTimeout(hoverTimeoutRef.current);
    }

    hoverTimeoutRef.current = window.setTimeout(() => {
      closePopover();
    }, mouseLeaveDelay);
  };

  const handleClick = () => {
    if (triggers.includes('click')) {
      togglePopover();
    }
  };

  const handleFocus = () => {
    if (triggers.includes('focus')) {
      openPopover();
    }
  };

  const handleBlur = () => {
    if (triggers.includes('focus')) {
      closePopover();
    }
  };

  // Clone child to attach ref and events
  const renderTrigger = () => {
    if (!isValidElement(children)) {
      return <span ref={triggerRef as any}>{children}</span>;
    }

    return cloneElement(children as ReactElement, {
      ref: triggerRef,
      onMouseEnter: handleMouseEnter,
      onMouseLeave: handleMouseLeave,
      onClick: (e: React.MouseEvent) => {
        handleClick();
        (children as ReactElement).props.onClick?.(e);
      },
      onFocus: (e: React.FocusEvent) => {
        handleFocus();
        (children as ReactElement).props.onFocus?.(e);
      },
      onBlur: (e: React.FocusEvent) => {
        handleBlur();
        (children as ReactElement).props.onBlur?.(e);
      },
    });
  };

  // Popover styles
  const popoverStyle: CSSProperties = {
    position: 'fixed',
    top: position?.top ?? 0,
    left: position?.left ?? 0,
    zIndex,
    backgroundColor: '#fff',
    borderRadius: 8,
    boxShadow: '0 3px 6px -4px rgba(0,0,0,0.12), 0 6px 16px 0 rgba(0,0,0,0.08), 0 9px 28px 8px rgba(0,0,0,0.05)',
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    fontSize: '14px',
    maxWidth: 300,
    opacity: position ? 1 : 0,
    visibility: position ? 'visible' : 'hidden',
    transition: 'opacity 0.2s ease',
    ...overlayStyle,
  };

  // Arrow styles
  const arrowBaseStyle: CSSProperties = {
    width: 16,
    height: 16,
    backgroundColor: '#fff',
    boxShadow: '3px 3px 7px rgba(0,0,0,0.07)',
    ...position?.arrowStyle,
  };

  // Title styles
  const titleStyle: CSSProperties = {
    padding: '8px 12px',
    borderBottom: content ? '1px solid #e0e0e0' : undefined,
    fontWeight: 500,
    color: 'rgba(0, 0, 0, 0.87)',
    minWidth: 150,
  };

  // Content styles
  const contentStyle: CSSProperties = {
    padding: '12px',
    color: 'rgba(0, 0, 0, 0.65)',
    ...overlayInnerStyle,
  };

  // Should render popover?
  const shouldRender = isOpen || !destroyTooltipOnHide;

  // Render popover content
  const popoverContent = shouldRender ? (
    <div
      ref={popoverRef}
      style={{
        ...popoverStyle,
        display: isOpen ? 'block' : 'none',
      }}
      className={overlayClassName}
      data-testid={dataTestId}
      data-placement={placement}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      {/* Arrow */}
      {arrow && <div style={arrowBaseStyle} />}

      {/* Title */}
      {title && <div style={titleStyle}>{title}</div>}

      {/* Content */}
      {content && <div style={contentStyle}>{content}</div>}
    </div>
  ) : null;

  // Container for portal
  const container = getPopupContainer?.() || document.body;

  return (
    <>
      {/* Trigger element */}
      <span style={style} className={className}>
        {renderTrigger()}
      </span>

      {/* Popover (in portal) */}
      {popoverContent && createPortal(popoverContent, container)}
    </>
  );
};

NativePopover.displayName = 'NativePopover';

export default NativePopover;
