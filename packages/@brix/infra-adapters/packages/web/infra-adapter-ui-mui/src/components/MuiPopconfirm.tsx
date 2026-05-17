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
 * @file MUI Popconfirm Component Implementation
 * @description Material UI implementation of the Popconfirm confirmation component
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiPopconfirm
 * @version 1.0.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Implements UIAdapter contract for Popconfirm component
 * - Composed from MUI Popover + Button for confirmation pattern
 * - All Popconfirm usage must go through useUI() hook
 */

import React, { type FC, useState, useCallback, useRef } from 'react';
import Popover from '@mui/material/Popover';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import MuiButton from '@mui/material/Button';
import type {
  PopconfirmProps,
  PopconfirmPlacement,
} from '@brix-sdk/runtime-sdk-api-web';

const PLACEMENT_MAP: Record<
  PopconfirmPlacement,
  {
    anchorOrigin: {
      vertical: 'top' | 'center' | 'bottom';
      horizontal: 'left' | 'center' | 'right';
    };
    transformOrigin: {
      vertical: 'top' | 'center' | 'bottom';
      horizontal: 'left' | 'center' | 'right';
    };
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
  right: {
    anchorOrigin: { vertical: 'center', horizontal: 'right' },
    transformOrigin: { vertical: 'center', horizontal: 'left' },
  },
};

export const MuiPopconfirm: FC<PopconfirmProps> = ({
  title,
  onConfirm,
  onCancel,
  okText = 'OK',
  cancelText = 'Cancel',
  disabled = false,
  placement = 'top',
  style,
  className,
  children,
}) => {
  const [open, setOpen] = useState(false);
  const anchorRef = useRef<HTMLElement | null>(null);

  const handleTriggerClick = useCallback(
    (e: React.MouseEvent) => {
      if (disabled) return;
      anchorRef.current = e.currentTarget as HTMLElement;
      setOpen(true);
    },
    [disabled],
  );

  const handleClose = useCallback(() => {
    setOpen(false);
    onCancel?.();
  }, [onCancel]);

  const handleConfirm = useCallback(() => {
    setOpen(false);
    onConfirm?.();
  }, [onConfirm]);

  const { anchorOrigin, transformOrigin } = PLACEMENT_MAP[placement] ?? PLACEMENT_MAP.top;

  return (
    <>
      <span onClick={handleTriggerClick} style={{ display: 'inline-block' }}>
        {children}
      </span>
      <Popover
        open={open}
        anchorEl={anchorRef.current}
        onClose={handleClose}
        anchorOrigin={anchorOrigin}
        transformOrigin={transformOrigin}
        className={className}
        slotProps={{
          paper: {
            sx: {
              p: 2,
              minWidth: 200,
              ...style,
            },
          },
        }}
      >
        <Typography variant="body2" sx={{ mb: 1.5 }}>
          {title}
        </Typography>
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
          <MuiButton size="small" onClick={handleClose}>
            {cancelText}
          </MuiButton>
          <MuiButton size="small" variant="contained" color="primary" onClick={handleConfirm}>
            {okText}
          </MuiButton>
        </Box>
      </Popover>
    </>
  );
};
