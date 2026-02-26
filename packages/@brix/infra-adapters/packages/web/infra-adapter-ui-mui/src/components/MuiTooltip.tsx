/**
 * @file MUI Tooltip Component
 * @description Material UI implementation of TooltipProps from UIAdapter contract.
 *              Informative text that appears on hover or focus.
 * @module @brix/infra-adapter-ui-mui/components/MuiTooltip
 * @version 3.1.0
 *
 * [Design Principles]
 * - Direct mapping from TooltipProps to MUI Tooltip API
 * - Supports all placement positions
 * - Configurable delay and arrow
 * - Full accessibility via MUI
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This is an atomic component in the infra-adapters layer.
 * Shell layer uses this for Help text and button hints.
 */

import type { FC } from 'react';
import type { TooltipProps, TooltipPlacement } from '@brix/runtime-sdk-api-web';
import Tooltip from '@mui/material/Tooltip';

// ============================================================================
// Placement Mappings
// ============================================================================

/**
 * Maps UIAdapter TooltipPlacement to MUI Tooltip placement
 *
 * <p>MUI uses lowercase hyphenated placement names.</p>
 */
const PLACEMENT_MAP: Record<
  TooltipPlacement,
  | 'top'
  | 'top-start'
  | 'top-end'
  | 'bottom'
  | 'bottom-start'
  | 'bottom-end'
  | 'left'
  | 'left-start'
  | 'left-end'
  | 'right'
  | 'right-start'
  | 'right-end'
> = {
  'top': 'top',
  'top-start': 'top-start',
  'top-end': 'top-end',
  'bottom': 'bottom',
  'bottom-start': 'bottom-start',
  'bottom-end': 'bottom-end',
  'left': 'left',
  'left-start': 'left-start',
  'left-end': 'left-end',
  'right': 'right',
  'right-start': 'right-start',
  'right-end': 'right-end',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Tooltip Component
 *
 * <p>Material UI implementation of TooltipProps from UIAdapter contract.
 * Displays informative text when users hover over or focus on an element.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>All 12 placement positions supported</li>
 *   <li>Configurable enter/leave delays</li>
 *   <li>Optional arrow indicator</li>
 *   <li>Rich content support (not just text)</li>
 *   <li>Full accessibility via MUI</li>
 * </ul>
 *
 * <h3>Accessibility:</h3>
 * <p>MUI Tooltip automatically handles ARIA attributes for screen readers.</p>
 *
 * @example
 * ```tsx
 * // Basic tooltip
 * const { Tooltip, Button } = useUI();
 *
 * <Tooltip title="Save your changes">
 *   <Button variant="primary">Save</Button>
 * </Tooltip>
 *
 * // Tooltip with arrow and placement
 * <Tooltip
 *   title="Delete this item"
 *   placement="right"
 *   arrow
 * >
 *   <Button variant="danger">Delete</Button>
 * </Tooltip>
 *
 * // Rich content tooltip
 * <Tooltip
 *   title={
 *     <div>
 *       <strong>Tip:</strong> Use keyboard shortcuts
 *     </div>
 *   }
 * >
 *   <Icon name="help" />
 * </Tooltip>
 * ```
 *
 * @param props - TooltipProps from UIAdapter contract
 * @returns MUI Tooltip component
 */
export const MuiTooltip: FC<TooltipProps> = ({
  title,
  placement = 'top',
  arrow = true,
  enterDelay = 100,
  leaveDelay = 0,
  disabled = false,
  style,
  className,
  children,
}) => {
  // Don't render tooltip when disabled
  // Just return the children directly
  if (disabled) {
    return <>{children}</>;
  }

  return (
    <Tooltip
      title={title}
      placement={PLACEMENT_MAP[placement]}
      arrow={arrow}
      enterDelay={enterDelay}
      leaveDelay={leaveDelay}
      componentsProps={{
        tooltip: {
          sx: style,
          className,
        },
      }}
    >
      {/* Wrap in span if children is not a valid element for ref forwarding */}
      <span style={{ display: 'inline-flex' }}>{children}</span>
    </Tooltip>
  );
};

export default MuiTooltip;
