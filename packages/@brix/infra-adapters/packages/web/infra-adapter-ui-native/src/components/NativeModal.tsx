/**
 * @file Native Modal Component
 * @description Pure CSS modal/dialog component implementing ModalProps from UIAdapter contract.
 * @module @brix/infra-adapter-ui-native/components/NativeModal
 * @version 3.1.0
 */

import {
  useEffect,
  useCallback,
  type FC,
  type CSSProperties,
} from 'react';
import { createPortal } from 'react-dom';
import type { ModalProps, ModalSize } from '@brix/runtime-sdk-api-web';
import { NativeIcon } from '../icons';
import { NativeButton } from './NativeButton';

// ============================================================================
// Style Constants
// ============================================================================

/**
 * Modal size widths
 */
const SIZE_WIDTHS: Record<ModalSize, string> = {
  small: '400px',
  medium: '600px',
  large: '900px',
  fullscreen: '100vw',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Modal Component
 *
 * <p>Pure CSS modal implementing ModalProps from UIAdapter contract.</p>
 */
export const NativeModal: FC<ModalProps> = ({
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
  confirmLoading = false,
  onClose,
  onConfirm,
  onCancel,
  afterOpen,
  afterClose,
  style,
  className,
  children,
}) => {
  // Handle escape key press
  const handleKeyDown = useCallback((event: KeyboardEvent) => {
    if (closeOnEscape && event.key === 'Escape') {
      onClose();
    }
  }, [closeOnEscape, onClose]);

  // Add/remove escape key listener
  useEffect(() => {
    if (open) {
      document.addEventListener('keydown', handleKeyDown);
      // Prevent body scroll when modal is open
      document.body.style.overflow = 'hidden';
      // Call afterOpen callback
      afterOpen?.();
    }

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = '';
      if (!open) {
        afterClose?.();
      }
    };
  }, [open, handleKeyDown, afterOpen, afterClose]);

  // Handle overlay click
  const handleOverlayClick = () => {
    if (closeOnOverlayClick) {
      onClose();
    }
  };

  // Prevent click propagation from modal content
  const handleContentClick = (event: React.MouseEvent) => {
    event.stopPropagation();
  };

  // Handle cancel button
  const handleCancel = () => {
    onCancel?.();
    onClose();
  };

  // Don't render if not open
  if (!open) return null;

  // Overlay style
  const overlayStyle: CSSProperties = {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    display: 'flex',
    alignItems: centered ? 'center' : 'flex-start',
    justifyContent: 'center',
    padding: centered ? '0' : '72px 16px 16px',
    zIndex: 1300,
    overflow: 'auto',
  };

  // Modal content style
  const contentStyle: CSSProperties = {
    backgroundColor: '#ffffff',
    borderRadius: size === 'fullscreen' ? '0' : '8px',
    boxShadow: '0 11px 15px -7px rgba(0,0,0,0.2), 0 24px 38px 3px rgba(0,0,0,0.14), 0 9px 46px 8px rgba(0,0,0,0.12)',
    width: width ?? SIZE_WIDTHS[size],
    maxWidth: size === 'fullscreen' ? '100%' : 'calc(100vw - 32px)',
    maxHeight: size === 'fullscreen' ? '100vh' : 'calc(100vh - 96px)',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
    ...style,
  };

  // Header style
  const headerStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '16px 24px',
    borderBottom: '1px solid rgba(0, 0, 0, 0.12)',
    flexShrink: 0,
  };

  // Title style
  const titleStyle: CSSProperties = {
    margin: 0,
    fontSize: '18px',
    fontWeight: 500,
    color: 'rgba(0, 0, 0, 0.87)',
  };

  // Close button style
  const closeButtonStyle: CSSProperties = {
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    padding: '4px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'rgba(0, 0, 0, 0.54)',
    borderRadius: '50%',
    transition: 'background-color 0.2s',
  };

  // Body style
  const bodyStyle: CSSProperties = {
    padding: '24px',
    overflow: 'auto',
    flex: 1,
  };

  // Footer style
  const footerStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: '8px',
    padding: '16px 24px',
    borderTop: '1px solid rgba(0, 0, 0, 0.12)',
    flexShrink: 0,
  };

  // Render default footer if not provided and onConfirm exists
  const renderFooter = () => {
    // If footer is explicitly null, don't render
    if (footer === null) return null;

    // If custom footer provided, use it
    if (footer !== undefined) {
      return <div style={footerStyle}>{footer}</div>;
    }

    // Default footer with confirm/cancel buttons
    if (onConfirm || onCancel) {
      return (
        <div style={footerStyle}>
          <NativeButton variant="text" onClick={handleCancel}>
            {cancelText}
          </NativeButton>
          {onConfirm && (
            <NativeButton
              variant="primary"
              onClick={onConfirm}
              loading={confirmLoading}
            >
              {confirmText}
            </NativeButton>
          )}
        </div>
      );
    }

    return null;
  };

  // Create portal to render modal at document body
  const modalContent = (
    <div
      style={overlayStyle}
      onClick={handleOverlayClick}
      role="dialog"
      aria-modal="true"
      aria-labelledby={title ? 'modal-title' : undefined}
    >
      <div style={contentStyle} className={className} onClick={handleContentClick}>
        {/* Header */}
        {(title || showCloseButton) && (
          <div style={headerStyle}>
            {title && (
              typeof title === 'string'
                ? <h2 id="modal-title" style={titleStyle}>{title}</h2>
                : title
            )}
            {showCloseButton && (
              <button
                style={closeButtonStyle}
                onClick={onClose}
                aria-label="Close modal"
              >
                <NativeIcon name="close" size="small" />
              </button>
            )}
          </div>
        )}

        {/* Body */}
        <div style={bodyStyle}>{children}</div>

        {/* Footer */}
        {renderFooter()}
      </div>
    </div>
  );

  // Use portal if document.body exists (SSR safety)
  if (typeof document !== 'undefined') {
    return createPortal(modalContent, document.body);
  }

  return modalContent;
};

NativeModal.displayName = 'NativeModal';

export default NativeModal;
