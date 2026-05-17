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
 * @file Modal Component Type Definitions
 * @description Defines types for the Modal component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/modal
 * @version 3.2.0
 */

import type { ReactNode, CSSProperties } from 'react';

/**
 * Modal Size Variants
 */
export type ModalSize = 'small' | 'medium' | 'large' | 'fullscreen';

/**
 * Modal Component Props
 *
 * Dialog/modal component for displaying content in an overlay.
 *
 * @example
 * ```tsx
 * <Modal
 *   open={isOpen}
 *   title="Confirm Action"
 *   onClose={handleClose}
 *   onConfirm={handleConfirm}
 * >
 *   <p>Are you sure you want to proceed?</p>
 * </Modal>
 * ```
 */
export interface ModalProps {
  /**
   * Open State
   *
   * Whether the modal is visible.
   */
  open: boolean;

  /**
   * Modal Title
   */
  title?: ReactNode;

  /**
   * Modal Size
   *
   * @default 'medium'
   */
  size?: ModalSize;

  /**
   * Close on Overlay Click
   *
   * When true, clicking the overlay backdrop closes the modal.
   * @default true
   */
  closeOnOverlayClick?: boolean;

  /**
   * Close on Escape Key
   *
   * When true, pressing Escape closes the modal.
   * @default true
   */
  closeOnEscape?: boolean;

  /**
   * Show Close Button
   *
   * When true, displays a close button in the header.
   * @default true
   */
  showCloseButton?: boolean;

  /**
   * Centered Position
   *
   * When true, centers the modal vertically.
   * @default true
   */
  centered?: boolean;

  /**
   * Custom Width
   *
   * Custom width override (CSS value).
   */
  width?: string | number;

  /**
   * Footer Content
   *
   * Custom footer content. Set to null to hide footer.
   */
  footer?: ReactNode;

  /**
   * Confirm Button Text
   *
   * Text for the default confirm button.
   * @default 'OK'
   */
  confirmText?: string;

  /**
   * Cancel Button Text
   *
   * Text for the default cancel button.
   * @default 'Cancel'
   */
  cancelText?: string;

  /**
   * Confirm Button Loading
   *
   * When true, the confirm button shows loading state.
   * @default false
   */
  confirmLoading?: boolean;

  /**
   * Close Handler
   *
   * Callback fired when the modal requests to be closed.
   */
  onClose: () => void;

  /**
   * Confirm Handler
   *
   * Callback fired when the confirm button is clicked.
   */
  onConfirm?: () => void;

  /**
   * Cancel Handler
   *
   * Callback fired when the cancel button is clicked.
   */
  onCancel?: () => void;

  /**
   * After Open Handler
   *
   * Callback fired after the modal has opened.
   */
  afterOpen?: () => void;

  /**
   * After Close Handler
   *
   * Callback fired after the modal has closed.
   */
  afterClose?: () => void;

  /**
   * Custom Inline Styles
   *
   * Styles applied to the modal content container.
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;

  /**
   * Test ID
   *
   * Data attribute for testing frameworks.
   */
  'data-testid'?: string;

  /**
   * Modal Content
   */
  children?: ReactNode;
}
