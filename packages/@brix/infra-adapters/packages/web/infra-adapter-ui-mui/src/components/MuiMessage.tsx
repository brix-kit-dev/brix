/**
 * @file MUI Message API
 * @description Material UI implementation of MessageAPI from UIAdapter contract.
 *              Imperative toast/snackbar notification system using MUI Snackbar.
 * @module @brix/infra-adapter-ui-mui/components/MuiMessage
 * @version 3.1.0
 *
 * [Design Principles]
 * - Imperative API matching UIAdapter MessageAPI interface
 * - Uses React 18 createRoot for portal rendering
 * - Stacking support for multiple simultaneous messages
 * - Automatic cleanup on dismiss
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This is an atomic feedback component in the infra-adapters layer.
 * Used for success/error/warning notifications across the application.
 */

import { createRoot, type Root } from 'react-dom/client';
import Snackbar from '@mui/material/Snackbar';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import IconButton from '@mui/material/IconButton';
import CloseIcon from '@mui/icons-material/Close';
import type {
  MessageAPI,
  MessageOptions,
  MessageType,
  MessageDestroy,
} from '@brix/runtime-sdk-api-web';
import { createElement, type ReactNode } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Internal message state
 */
interface MessageState {
  key: string;
  type: MessageType;
  content: ReactNode;
  duration: number;
  closable: boolean;
  onClose?: () => void;
  root?: Root;
  container?: HTMLDivElement;
}

// ============================================================================
// Message Manager
// ============================================================================

/**
 * Message Manager Class
 *
 * <p>Manages a collection of toast messages with automatic cleanup.
 * Uses React 18 createRoot API for portal rendering.</p>
 */
class MuiMessageManager {
  /** Map of active messages by key */
  private messages = new Map<string, MessageState>();

  /** Counter for generating unique keys */
  private keyCounter = 0;

  /** Container element for message portals */
  private containerElement: HTMLDivElement | null = null;

  /**
   * Generate a unique message key
   */
  private generateKey(): string {
    return `mui-message-${++this.keyCounter}`;
  }

  /**
   * Get or create the container element for messages
   */
  private getContainer(): HTMLDivElement {
    if (!this.containerElement) {
      this.containerElement = document.createElement('div');
      this.containerElement.id = 'mui-message-container';
      this.containerElement.style.cssText = `
        position: fixed;
        top: 24px;
        left: 50%;
        transform: translateX(-50%);
        z-index: 9999;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 8px;
        pointer-events: none;
      `;
      document.body.appendChild(this.containerElement);
    }
    return this.containerElement;
  }

  /**
   * Map message type to MUI Alert severity
   */
  private getSeverity(type: MessageType): 'success' | 'error' | 'warning' | 'info' {
    switch (type) {
      case 'success':
        return 'success';
      case 'error':
        return 'error';
      case 'warning':
        return 'warning';
      case 'loading':
      case 'info':
      default:
        return 'info';
    }
  }

  /**
   * Render a message using React portal
   */
  private renderMessage(state: MessageState): void {
    const container = document.createElement('div');
    container.style.pointerEvents = 'auto';
    this.getContainer().appendChild(container);

    const root = createRoot(container);
    state.container = container;
    state.root = root;

    const handleClose = () => {
      this.destroy(state.key);
      if (state.onClose) {
        state.onClose();
      }
    };

    // Create alert element
    const alertElement = createElement(Alert, {
      severity: this.getSeverity(state.type),
      variant: 'filled',
      sx: {
        minWidth: 200,
        boxShadow: 3,
      },
      icon: state.type === 'loading' ? createElement(CircularProgress, {
        size: 20,
        color: 'inherit',
      }) : undefined,
      action: state.closable ? createElement(IconButton, {
        size: 'small',
        color: 'inherit',
        onClick: handleClose,
      }, createElement(CloseIcon, { fontSize: 'small' })) : undefined,
    }, state.content);

    // Create snackbar wrapper
    const snackbarElement = createElement(Snackbar, {
      open: true,
      anchorOrigin: { vertical: 'top', horizontal: 'center' },
      autoHideDuration: state.duration > 0 ? state.duration : null,
      onClose: state.duration > 0 ? handleClose : undefined,
      sx: {
        position: 'static',
        transform: 'none',
      },
    }, alertElement);

    root.render(snackbarElement);
  }

  /**
   * Show a message
   *
   * @param type - Message type (success, error, warning, info, loading)
   * @param options - Message options
   * @returns Destroy function to manually close the message
   */
  show(type: MessageType, options: MessageOptions | string): MessageDestroy {
    const normalizedOptions: MessageOptions =
      typeof options === 'string' ? { content: options } : options;

    const key = normalizedOptions.key ?? this.generateKey();
    const duration = normalizedOptions.duration ?? (type === 'loading' ? 0 : 3000);

    // If message with same key exists, destroy it first
    if (this.messages.has(key)) {
      this.destroy(key);
    }

    const state: MessageState = {
      key,
      type,
      content: normalizedOptions.content,
      duration,
      closable: normalizedOptions.closable ?? false,
      onClose: normalizedOptions.onClose,
    };

    this.messages.set(key, state);
    this.renderMessage(state);

    return () => this.destroy(key);
  }

  /**
   * Destroy a specific message by key
   */
  destroy(key?: string): void {
    if (!key) {
      // Destroy all if no key specified
      this.destroyAll();
      return;
    }

    const state = this.messages.get(key);
    if (state) {
      if (state.root) {
        state.root.unmount();
      }
      if (state.container && state.container.parentNode) {
        state.container.parentNode.removeChild(state.container);
      }
      this.messages.delete(key);
    }
  }

  /**
   * Destroy all messages
   */
  destroyAll(): void {
    this.messages.forEach((state) => {
      if (state.root) {
        state.root.unmount();
      }
      if (state.container && state.container.parentNode) {
        state.container.parentNode.removeChild(state.container);
      }
    });
    this.messages.clear();
  }
}

// ============================================================================
// Singleton Instance
// ============================================================================

/**
 * Global message manager instance
 */
const messageManager = new MuiMessageManager();

// ============================================================================
// MessageAPI Implementation
// ============================================================================

/**
 * MUI Message API
 *
 * <p>Material UI implementation of MessageAPI from UIAdapter contract.
 * Provides an imperative API for displaying toast notifications.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Five message types: success, error, warning, info, loading</li>
 *   <li>Configurable duration with auto-dismiss</li>
 *   <li>Manual dismiss via returned destroy function</li>
 *   <li>Closable option with close button</li>
 *   <li>Message key for updating existing messages</li>
 *   <li>Stack multiple messages vertically</li>
 * </ul>
 *
 * @example
 * ```tsx
 * // Using message API from UIAdapter
 * const { message } = useUI();
 *
 * // Success message
 * message.success('Saved successfully!');
 *
 * // Error with options
 * message.error({
 *   content: 'Operation failed',
 *   duration: 5000,
 *   closable: true,
 * });
 *
 * // Loading with manual dismiss
 * const destroy = message.loading('Processing...');
 * await doAsyncWork();
 * destroy();
 * message.success('Done!');
 *
 * // Update existing message
 * message.loading({ content: 'Step 1...', key: 'progress' });
 * await step1();
 * message.loading({ content: 'Step 2...', key: 'progress' });
 * await step2();
 * message.success({ content: 'Complete!', key: 'progress' });
 * ```
 */
export const muiMessageAPI: MessageAPI = {
  success: (options) => messageManager.show('success', options),
  error: (options) => messageManager.show('error', options),
  warning: (options) => messageManager.show('warning', options),
  info: (options) => messageManager.show('info', options),
  loading: (options) => messageManager.show('loading', options),
  destroy: (key) => messageManager.destroy(key),
  destroyAll: () => messageManager.destroyAll(),
};

export default muiMessageAPI;
