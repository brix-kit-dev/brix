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
 * @file MUI Modal Component
 * @description Material UI implementation of ModalProps from UIAdapter contract.
 *              Dialog/overlay component for displaying content in a modal.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiModal
 * @version 3.1.0
 *
 * [Design Principles]
 * - Direct mapping from ModalProps to MUI Dialog API
 * - Supports title, content, and footer areas
 * - Configurable size with fullscreen option
 * - Close on overlay click and escape key
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This is an atomic component in the infra-adapters layer.
 * Used for confirmation dialogs, forms, and content overlays.
 */

import type { FC } from 'react';
import { useCallback, useEffect } from 'react';
import type { ModalProps, ModalSize } from '@brix-sdk/runtime-sdk-api-web';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import IconButton from '@mui/material/IconButton';
import Button from '@mui/material/Button';
import CloseIcon from '@mui/icons-material/Close';

// ============================================================================
// Size Mappings
// ============================================================================

/**
 * Maps UIAdapter ModalSize to MUI Dialog maxWidth
 */
const SIZE_MAP: Record<ModalSize, 'xs' | 'sm' | 'md' | 'lg' | 'xl' | false> = {
  small: 'sm',
  medium: 'md',
  large: 'lg',
  fullscreen: false, // false enables fullScreen prop
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Modal Component
 *
 * <p>Material UI implementation of ModalProps from UIAdapter contract.
 * Provides a dialog overlay for displaying content, forms, and confirmations.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Configurable size (small, medium, large, fullscreen)</li>
 *   <li>Title with optional close button</li>
 *   <li>Customizable footer or default confirm/cancel buttons</li>
 *   <li>Close on overlay click (optional)</li>
 *   <li>Close on escape key (optional)</li>
 *   <li>Loading state for confirm button</li>
 *   <li>Lifecycle callbacks (afterOpen, afterClose)</li>
 * </ul>
 *
 * <h3>Architectural Constraints:</h3>
 * <ul>
 *   <li>This component is an atomic building block</li>
 *   <li>Confirmation dialogs use this via UIAdapter</li>
 *   <li>No direct import allowed in Plugin layer</li>
 * </ul>
 *
 * @example
 * ```tsx
 * // Confirmation modal
 * const { Modal, Button } = useUI();
 *
 * <Modal
 *   open={isOpen}
 *   title="Confirm Delete"
 *   onClose={handleClose}
 *   onConfirm={handleDelete}
 *   confirmText="Delete"
 * >
 *   <p>Are you sure you want to delete this item?</p>
 * </Modal>
 *
 * // Form modal with custom footer
 * <Modal
 *   open={isFormOpen}
 *   title="Edit Profile"
 *   size="large"
 *   onClose={handleClose}
 *   footer={
 *     <>
 *       <Button variant="text" onClick={handleClose}>Cancel</Button>
 *       <Button variant="primary" onClick={handleSave} loading={saving}>
 *         Save
 *       </Button>
 *     </>
 *   }
 * >
 *   <ProfileForm />
 * </Modal>
 * ```
 *
 * @param props - ModalProps from UIAdapter contract
 * @returns MUI Dialog component
 */
export const MuiModal: FC<ModalProps> = ({
  open,
  title,
  size = 'medium',
  closeOnOverlayClick = true,
  closeOnEscape = true,
  showCloseButton = true,
  centered = true,
  width,
  footer,
  confirmText = 'OK',
  cancelText = 'Cancel',
  showCancel = true,
  confirmLoading = false,
  onClose,
  onConfirm,
  onCancel,
  afterOpen,
  afterClose,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Determine if fullscreen mode
  const isFullscreen = size === 'fullscreen';

  /**
   * Handle backdrop click
   *
   * <p>Only closes the modal if closeOnOverlayClick is true.</p>
   */
  const handleBackdropClick = useCallback(
    (_event: object, reason: string) => {
      if (reason === 'backdropClick' && !closeOnOverlayClick) {
        return;
      }
      if (reason === 'escapeKeyDown' && !closeOnEscape) {
        return;
      }
      onClose();
    },
    [closeOnOverlayClick, closeOnEscape, onClose]
  );

  /**
   * Handle cancel button click
   */
  const handleCancel = useCallback(() => {
    if (onCancel) {
      onCancel();
    } else {
      onClose();
    }
  }, [onCancel, onClose]);

  // Call afterOpen callback when modal opens
  useEffect(() => {
    if (open && afterOpen) {
      afterOpen();
    }
  }, [open, afterOpen]);

  // Call afterClose callback when modal closes
  useEffect(() => {
    if (!open && afterClose) {
      afterClose();
    }
  }, [open, afterClose]);

  // Build default footer if not custom provided
  const defaultFooter = (
    <>
      {showCancel && (
        <Button
          onClick={handleCancel}
          color="inherit"
          data-testid={dataTestId ? `${dataTestId}-cancel` : undefined}
        >
          {cancelText}
        </Button>
      )}
      {onConfirm && (
        <Button
          onClick={onConfirm}
          variant="contained"
          disabled={confirmLoading}
          data-testid={dataTestId ? `${dataTestId}-submit` : undefined}
        >
          {confirmLoading ? 'Loading...' : confirmText}
        </Button>
      )}
    </>
  );

  return (
    <Dialog
      open={open}
      onClose={handleBackdropClick}
      fullScreen={isFullscreen}
      maxWidth={isFullscreen ? false : SIZE_MAP[size]}
      fullWidth={!isFullscreen}
      scroll="paper"
      sx={{
        '& .MuiDialog-paper': {
          width: width ?? undefined,
          ...style,
        },
        ...(centered && {
          '& .MuiDialog-container': {
            alignItems: 'center',
          },
        }),
      }}
      className={className}
      data-testid={dataTestId}
    >
      {/* Dialog title with optional close button */}
      {(title || showCloseButton) && (
        <DialogTitle
          sx={{
            m: 0,
            p: 2,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          {title}
          {showCloseButton && (
            <IconButton
              aria-label="close"
              onClick={onClose}
              data-testid={dataTestId ? `${dataTestId}-close` : undefined}
              sx={{
                color: (theme) => theme.palette.grey[500],
              }}
            >
              <CloseIcon />
            </IconButton>
          )}
        </DialogTitle>
      )}

      {/* Dialog content */}
      <DialogContent dividers>{children}</DialogContent>

      {/* Dialog footer - custom or default */}
      {footer !== null && (
        <DialogActions>
          {footer ?? defaultFooter}
        </DialogActions>
      )}
    </Dialog>
  );
};

export default MuiModal;
