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
 * @file MUI Drawer Component Implementation
 * @description Material UI implementation of the Drawer panel component
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiDrawer
 * @version 1.0.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Implements UIAdapter contract for Drawer component
 * - Maps MUI Drawer API to unified BrixUI interface
 * - All drawer components must be obtained via useUI() hook
 *
 * @example
 * ```tsx
 * import { useUI } from '@brix-sdk/runtime-sdk-api-web';
 *
 * function SettingsPanel() {
 *   const { Drawer, Button } = useUI();
 *   const [open, setOpen] = useState(false);
 *
 *   return (
 *     <>
 *       <Button onClick={() => setOpen(true)}>Settings</Button>
 *       <Drawer
 *         open={open}
 *         title="Settings"
 *         onClose={() => setOpen(false)}
 *       >
 *         <SettingsForm />
 *       </Drawer>
 *     </>
 *   );
 * }
 * ```
 */

import React, { type FC, useCallback } from 'react';
import Drawer from '@mui/material/Drawer';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Divider from '@mui/material/Divider';
import CloseIcon from '@mui/icons-material/Close';
import type {
  DrawerProps,
  DrawerPlacement,
  DrawerSize,
} from '@brix-sdk/runtime-sdk-api-web';

/**
 * Placement to MUI Anchor Mapping
 */
const ANCHOR_MAP: Record<DrawerPlacement, 'left' | 'right' | 'top' | 'bottom'> = {
  left: 'left',
  right: 'right',
  top: 'top',
  bottom: 'bottom',
};

/**
 * Default Size Values (for left/right placement)
 */
const SIZE_WIDTH_MAP: Record<DrawerSize, number> = {
  default: 378,
  large: 736,
};

/**
 * Default Size Values (for top/bottom placement)
 */
const SIZE_HEIGHT_MAP: Record<DrawerSize, number> = {
  default: 256,
  large: 400,
};

/**
 * MUI Drawer Component
 *
 * Material UI implementation of the UIAdapter Drawer interface.
 * Provides slide-in panel for secondary content or forms.
 *
 * **Features:**
 * - Four placement options: left, right, top, bottom
 * - Configurable size or custom width/height
 * - Header with title and close button
 * - Optional footer for actions
 * - Mask backdrop with configurable click behavior
 *
 * @param props - DrawerProps from UIAdapter contract
 * @returns React element
 */
export const MuiDrawer: FC<DrawerProps> = ({
  open,
  title,
  placement = 'right',
  size = 'default',
  width,
  height,
  closable = true,
  maskClosable = true,
  mask = true,
  keyboard = true,
  zIndex = 1000,
  footer,
  extra,
  destroyOnClose = false,
  loading = false,
  onClose,
  afterOpenChange,
  className,
  style,
  headerStyle,
  bodyStyle,
  footerStyle,
  children,
}) => {
  const isVertical = placement === 'left' || placement === 'right';

  /**
   * Calculate drawer dimensions
   */
  const getDimensions = () => {
    if (isVertical) {
      return {
        width: width ?? SIZE_WIDTH_MAP[size],
        height: '100%',
      };
    }
    return {
      width: '100%',
      height: height ?? SIZE_HEIGHT_MAP[size],
    };
  };

  /**
   * Handle close
   */
  const handleClose = useCallback(
    (event: object, reason: 'backdropClick' | 'escapeKeyDown') => {
      if (reason === 'backdropClick' && !maskClosable) {
        return;
      }
      if (reason === 'escapeKeyDown' && !keyboard) {
        return;
      }
      onClose?.();
    },
    [maskClosable, keyboard, onClose]
  );

  /**
   * Handle close button click
   */
  const handleCloseClick = useCallback(() => {
    onClose?.();
  }, [onClose]);

  /**
   * Handle transition end
   */
  const handleTransitionEnd = useCallback(() => {
    afterOpenChange?.(open);
  }, [afterOpenChange, open]);

  const dimensions = getDimensions();

  return (
    <Drawer
      open={open}
      anchor={ANCHOR_MAP[placement]}
      onClose={handleClose}
      onTransitionEnd={handleTransitionEnd}
      variant={mask ? 'temporary' : 'persistent'}
      ModalProps={{
        keepMounted: !destroyOnClose,
        disableEscapeKeyDown: !keyboard,
      }}
      PaperProps={{
        className,
        style: {
          ...dimensions,
          ...style,
        },
        sx: {
          display: 'flex',
          flexDirection: 'column',
          zIndex,
        },
      }}
      hideBackdrop={!mask}
    >
      {/* Header */}
      {(title || closable || extra) && (
        <>
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              p: 2,
              minHeight: 56,
            }}
            style={headerStyle}
          >
            <Typography variant="h6" component="div" sx={{ fontWeight: 500 }}>
              {title}
            </Typography>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              {extra}
              {closable && (
                <IconButton
                  aria-label="close"
                  onClick={handleCloseClick}
                  size="small"
                  edge="end"
                >
                  <CloseIcon />
                </IconButton>
              )}
            </Box>
          </Box>
          <Divider />
        </>
      )}

      {/* Body */}
      <Box
        sx={{
          flex: 1,
          overflow: 'auto',
          p: 2,
          position: 'relative',
        }}
        style={bodyStyle}
      >
        {loading && (
          <Box
            sx={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'rgba(255, 255, 255, 0.7)',
              zIndex: 1,
            }}
          >
            {/* Loading spinner would be rendered here */}
          </Box>
        )}
        {children}
      </Box>

      {/* Footer */}
      {footer && (
        <>
          <Divider />
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'flex-end',
              p: 2,
              gap: 1,
            }}
            style={footerStyle}
          >
            {footer}
          </Box>
        </>
      )}
    </Drawer>
  );
};

export default MuiDrawer;
