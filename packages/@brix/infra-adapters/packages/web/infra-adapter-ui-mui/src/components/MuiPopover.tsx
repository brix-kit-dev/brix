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
 * @file MUI Popover Component Implementation
 * @description Material UI implementation of the Popover floating card component
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiPopover
 * @version 1.0.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Implements UIAdapter contract for Popover component
 * - Maps MUI Popover API to unified BrixUI interface
 * - All popover components must be obtained via useUI() hook
 *
 * @example
 * ```tsx
 * import { useUI } from '@brix-sdk/runtime-sdk-api-web';
 *
 * function UserCard() {
 *   const { Popover, Avatar, Typography } = useUI();
 *
 *   return (
 *     <Popover
 *       title="User Profile"
 *       content={
 *         <Stack spacing={8}>
 *           <Avatar src={user.avatar} />
 *           <Typography>{user.name}</Typography>
 *         </Stack>
 *       }
 *     >
 *       <Avatar src={user.avatar} size="small" />
 *     </Popover>
 *   );
 * }
 * ```
 */

import React, {
  type FC,
  type ReactElement,
  useState,
  useCallback,
  useRef,
  useEffect,
} from 'react';
import Popover from '@mui/material/Popover';
import Paper from '@mui/material/Paper';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import type {
  PopoverProps,
  PopoverPlacement,
  PopoverTrigger,
} from '@brix-sdk/runtime-sdk-api-web';

/**
 * Placement to MUI Anchor/Transform Origin Mapping
 *
 * Maps BrixUI placement values to MUI Popover anchor and transform origins.
 */
const PLACEMENT_MAP: Record<
  PopoverPlacement,
  {
    anchorOrigin: { vertical: 'top' | 'center' | 'bottom'; horizontal: 'left' | 'center' | 'right' };
    transformOrigin: { vertical: 'top' | 'center' | 'bottom'; horizontal: 'left' | 'center' | 'right' };
  }
> = {
  top: {
    anchorOrigin: { vertical: 'top', horizontal: 'center' },
    transformOrigin: { vertical: 'bottom', horizontal: 'center' },
  },
  topLeft: {
    anchorOrigin: { vertical: 'top', horizontal: 'left' },
    transformOrigin: { vertical: 'bottom', horizontal: 'left' },
  },
  topRight: {
    anchorOrigin: { vertical: 'top', horizontal: 'right' },
    transformOrigin: { vertical: 'bottom', horizontal: 'right' },
  },
  bottom: {
    anchorOrigin: { vertical: 'bottom', horizontal: 'center' },
    transformOrigin: { vertical: 'top', horizontal: 'center' },
  },
  bottomLeft: {
    anchorOrigin: { vertical: 'bottom', horizontal: 'left' },
    transformOrigin: { vertical: 'top', horizontal: 'left' },
  },
  bottomRight: {
    anchorOrigin: { vertical: 'bottom', horizontal: 'right' },
    transformOrigin: { vertical: 'top', horizontal: 'right' },
  },
  left: {
    anchorOrigin: { vertical: 'center', horizontal: 'left' },
    transformOrigin: { vertical: 'center', horizontal: 'right' },
  },
  leftTop: {
    anchorOrigin: { vertical: 'top', horizontal: 'left' },
    transformOrigin: { vertical: 'top', horizontal: 'right' },
  },
  leftBottom: {
    anchorOrigin: { vertical: 'bottom', horizontal: 'left' },
    transformOrigin: { vertical: 'bottom', horizontal: 'right' },
  },
  right: {
    anchorOrigin: { vertical: 'center', horizontal: 'right' },
    transformOrigin: { vertical: 'center', horizontal: 'left' },
  },
  rightTop: {
    anchorOrigin: { vertical: 'top', horizontal: 'right' },
    transformOrigin: { vertical: 'top', horizontal: 'left' },
  },
  rightBottom: {
    anchorOrigin: { vertical: 'bottom', horizontal: 'right' },
    transformOrigin: { vertical: 'bottom', horizontal: 'left' },
  },
};

/**
 * MUI Popover Component
 *
 * Material UI implementation of the UIAdapter Popover interface.
 * Provides floating card with rich content on trigger interactions.
 *
 * **Features:**
 * - Multiple trigger types: hover, focus, click, contextMenu
 * - Configurable placement (12 positions)
 * - Title and content separation
 * - Controlled and uncontrolled modes
 * - Mouse enter/leave delays
 *
 * @param props - PopoverProps from UIAdapter contract
 * @returns React element
 */
export const MuiPopover: FC<PopoverProps> = ({
  title,
  content,
  open: controlledOpen,
  defaultOpen = false,
  placement = 'top',
  trigger = 'hover',
  arrow = true,
  mouseEnterDelay = 100,
  mouseLeaveDelay = 100,
  overlayStyle,
  overlayClassName,
  onOpenChange,
  getPopupContainer,
  destroyTooltipOnHide = false,
  color,
  className,
  style,
  children,
}) => {
  const [internalOpen, setInternalOpen] = useState(defaultOpen);
  const anchorRef = useRef<HTMLElement | null>(null);
  const enterTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const leaveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const isControlled = controlledOpen !== undefined;
  const isOpen = isControlled ? controlledOpen : internalOpen;

  /**
   * Normalize triggers to array
   */
  const triggers: PopoverTrigger[] = Array.isArray(trigger) ? trigger : [trigger];

  /**
   * Clear timers
   */
  const clearTimers = useCallback(() => {
    if (enterTimerRef.current) {
      clearTimeout(enterTimerRef.current);
      enterTimerRef.current = null;
    }
    if (leaveTimerRef.current) {
      clearTimeout(leaveTimerRef.current);
      leaveTimerRef.current = null;
    }
  }, []);

  /**
   * Handle open state change
   */
  const handleOpenChange = useCallback(
    (newOpen: boolean) => {
      if (!isControlled) {
        setInternalOpen(newOpen);
      }
      onOpenChange?.(newOpen);
    },
    [isControlled, onOpenChange]
  );

  /**
   * Handle mouse enter for hover trigger
   */
  const handleMouseEnter = useCallback(() => {
    if (!triggers.includes('hover')) return;

    clearTimers();
    enterTimerRef.current = setTimeout(() => {
      handleOpenChange(true);
    }, mouseEnterDelay);
  }, [triggers, clearTimers, mouseEnterDelay, handleOpenChange]);

  /**
   * Handle mouse leave for hover trigger
   */
  const handleMouseLeave = useCallback(() => {
    if (!triggers.includes('hover')) return;

    clearTimers();
    leaveTimerRef.current = setTimeout(() => {
      handleOpenChange(false);
    }, mouseLeaveDelay);
  }, [triggers, clearTimers, mouseLeaveDelay, handleOpenChange]);

  /**
   * Handle focus for focus trigger
   */
  const handleFocus = useCallback(() => {
    if (!triggers.includes('focus')) return;
    handleOpenChange(true);
  }, [triggers, handleOpenChange]);

  /**
   * Handle blur for focus trigger
   */
  const handleBlur = useCallback(() => {
    if (!triggers.includes('focus')) return;
    handleOpenChange(false);
  }, [triggers, handleOpenChange]);

  /**
   * Handle click for click trigger
   */
  const handleClick = useCallback(
    (event: React.MouseEvent) => {
      if (!triggers.includes('click')) return;
      event.stopPropagation();
      handleOpenChange(!isOpen);
    },
    [triggers, handleOpenChange, isOpen]
  );

  /**
   * Handle context menu for contextMenu trigger
   */
  const handleContextMenu = useCallback(
    (event: React.MouseEvent) => {
      if (!triggers.includes('contextMenu')) return;
      event.preventDefault();
      handleOpenChange(true);
    },
    [triggers, handleOpenChange]
  );

  /**
   * Handle popover close
   */
  const handleClose = useCallback(() => {
    handleOpenChange(false);
  }, [handleOpenChange]);

  /**
   * Clean up timers on unmount
   */
  useEffect(() => {
    return () => {
      clearTimers();
    };
  }, [clearTimers]);

  /**
   * Clone child with event handlers and ref
   */
  const childElement = React.Children.only(children) as ReactElement;
  const triggerElement = React.cloneElement(childElement, {
    ref: (node: HTMLElement | null) => {
      anchorRef.current = node;
      // Forward ref if child has one
      const { ref } = childElement as { ref?: React.Ref<HTMLElement> };
      if (typeof ref === 'function') {
        ref(node);
      } else if (ref && typeof ref === 'object') {
        (ref as React.MutableRefObject<HTMLElement | null>).current = node;
      }
    },
    onMouseEnter: (event: React.MouseEvent) => {
      handleMouseEnter();
      (childElement.props as { onMouseEnter?: (e: React.MouseEvent) => void })?.onMouseEnter?.(event);
    },
    onMouseLeave: (event: React.MouseEvent) => {
      handleMouseLeave();
      (childElement.props as { onMouseLeave?: (e: React.MouseEvent) => void })?.onMouseLeave?.(event);
    },
    onFocus: (event: React.FocusEvent) => {
      handleFocus();
      (childElement.props as { onFocus?: (e: React.FocusEvent) => void })?.onFocus?.(event);
    },
    onBlur: (event: React.FocusEvent) => {
      handleBlur();
      (childElement.props as { onBlur?: (e: React.FocusEvent) => void })?.onBlur?.(event);
    },
    onClick: (event: React.MouseEvent) => {
      handleClick(event);
      (childElement.props as { onClick?: (e: React.MouseEvent) => void })?.onClick?.(event);
    },
    onContextMenu: (event: React.MouseEvent) => {
      handleContextMenu(event);
      (childElement.props as { onContextMenu?: (e: React.MouseEvent) => void })?.onContextMenu?.(event);
    },
  });

  const placementConfig = PLACEMENT_MAP[placement];

  return (
    <>
      {triggerElement}
      <Popover
        open={isOpen}
        anchorEl={anchorRef.current}
        onClose={handleClose}
        anchorOrigin={placementConfig.anchorOrigin}
        transformOrigin={placementConfig.transformOrigin}
        className={overlayClassName}
        style={overlayStyle}
        disableRestoreFocus
        TransitionProps={{
          onExited: destroyTooltipOnHide ? () => {} : undefined,
        }}
        container={getPopupContainer?.() || undefined}
        onMouseEnter={triggers.includes('hover') ? handleMouseEnter : undefined}
        onMouseLeave={triggers.includes('hover') ? handleMouseLeave : undefined}
        slotProps={{
          paper: {
            sx: {
              ...(arrow && {
                mt: placement.startsWith('bottom') ? 1 : 0,
                mb: placement.startsWith('top') ? 1 : 0,
                ml: placement.startsWith('right') ? 1 : 0,
                mr: placement.startsWith('left') ? 1 : 0,
              }),
              ...(color && {
                bgcolor: color,
              }),
            },
          },
        }}
      >
        <Paper
          className={className}
          style={style}
          elevation={0}
          sx={{
            p: 0,
            minWidth: 180,
            maxWidth: 350,
          }}
        >
          {title && (
            <>
              <Box sx={{ px: 2, py: 1.5 }}>
                <Typography variant="subtitle2" fontWeight={600}>
                  {title}
                </Typography>
              </Box>
              <Divider />
            </>
          )}
          {content && (
            <Box sx={{ px: 2, py: 1.5 }}>
              {typeof content === 'string' ? (
                <Typography variant="body2">{content}</Typography>
              ) : (
                content
              )}
            </Box>
          )}
        </Paper>
      </Popover>
    </>
  );
};

export default MuiPopover;
