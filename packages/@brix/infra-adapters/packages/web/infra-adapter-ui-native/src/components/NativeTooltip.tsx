/**
 * @file Native Tooltip Component
 * @description Pure CSS tooltip component implementing TooltipProps from UIAdapter contract.
 * @module @brix/infra-adapter-ui-native/components/NativeTooltip
 * @version 3.1.0
 */

import {
  useState,
  useRef,
  useEffect,
  useCallback,
  cloneElement,
  isValidElement,
  type FC,
  type CSSProperties,
  type ReactElement,
} from 'react';
import type { TooltipProps, TooltipPlacement } from '@brix/runtime-sdk-api-web';

// ============================================================================
// Style Constants
// ============================================================================

/**
 * Tooltip positioning styles by placement
 */
const PLACEMENT_STYLES: Record<TooltipPlacement, CSSProperties> = {
  'top': { bottom: '100%', left: '50%', transform: 'translateX(-50%)', marginBottom: '8px' },
  'top-start': { bottom: '100%', left: '0', marginBottom: '8px' },
  'top-end': { bottom: '100%', right: '0', marginBottom: '8px' },
  'bottom': { top: '100%', left: '50%', transform: 'translateX(-50%)', marginTop: '8px' },
  'bottom-start': { top: '100%', left: '0', marginTop: '8px' },
  'bottom-end': { top: '100%', right: '0', marginTop: '8px' },
  'left': { right: '100%', top: '50%', transform: 'translateY(-50%)', marginRight: '8px' },
  'left-start': { right: '100%', top: '0', marginRight: '8px' },
  'left-end': { right: '100%', bottom: '0', marginRight: '8px' },
  'right': { left: '100%', top: '50%', transform: 'translateY(-50%)', marginLeft: '8px' },
  'right-start': { left: '100%', top: '0', marginLeft: '8px' },
  'right-end': { left: '100%', bottom: '0', marginLeft: '8px' },
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Tooltip Component
 *
 * <p>Pure CSS tooltip implementing TooltipProps from UIAdapter contract.</p>
 */
export const NativeTooltip: FC<TooltipProps> = ({
  title,
  placement = 'top',
  arrow: _arrow = true, // Reserved for future arrow styling
  enterDelay = 100,
  leaveDelay = 0,
  disabled = false,
  style,
  className,
  children,
}) => {
  const [visible, setVisible] = useState(false);
  const enterTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const leaveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Clean up timers on unmount
  useEffect(() => {
    return () => {
      if (enterTimerRef.current) clearTimeout(enterTimerRef.current);
      if (leaveTimerRef.current) clearTimeout(leaveTimerRef.current);
    };
  }, []);

  // Handle mouse enter
  const handleMouseEnter = useCallback(() => {
    if (disabled) return;
    if (leaveTimerRef.current) {
      clearTimeout(leaveTimerRef.current);
      leaveTimerRef.current = null;
    }
    enterTimerRef.current = setTimeout(() => {
      setVisible(true);
    }, enterDelay);
  }, [disabled, enterDelay]);

  // Handle mouse leave
  const handleMouseLeave = useCallback(() => {
    if (enterTimerRef.current) {
      clearTimeout(enterTimerRef.current);
      enterTimerRef.current = null;
    }
    leaveTimerRef.current = setTimeout(() => {
      setVisible(false);
    }, leaveDelay);
  }, [leaveDelay]);

  // Container style
  const containerStyle: CSSProperties = {
    display: 'inline-block',
    position: 'relative',
  };

  // Tooltip popup style
  const tooltipStyle: CSSProperties = {
    position: 'absolute',
    backgroundColor: 'rgba(97, 97, 97, 0.92)',
    color: '#ffffff',
    fontSize: '12px',
    padding: '6px 10px',
    borderRadius: '4px',
    whiteSpace: 'nowrap',
    zIndex: 1500,
    pointerEvents: 'none',
    opacity: visible ? 1 : 0,
    visibility: visible ? 'visible' : 'hidden',
    transition: 'opacity 0.2s, visibility 0.2s',
    ...PLACEMENT_STYLES[placement],
    ...style,
  };

  // Wrap children with event handlers
  const wrappedChildren = isValidElement(children)
    ? cloneElement(children as ReactElement, {
        onMouseEnter: handleMouseEnter,
        onMouseLeave: handleMouseLeave,
        onFocus: handleMouseEnter,
        onBlur: handleMouseLeave,
      })
    : children;

  return (
    <span
      style={containerStyle}
      className={className}
      onMouseEnter={!isValidElement(children) ? handleMouseEnter : undefined}
      onMouseLeave={!isValidElement(children) ? handleMouseLeave : undefined}
    >
      {wrappedChildren}
      
      {/* Tooltip popup */}
      {title && (
        <span style={tooltipStyle} role="tooltip">
          {title}
        </span>
      )}
    </span>
  );
};

NativeTooltip.displayName = 'NativeTooltip';

export default NativeTooltip;
