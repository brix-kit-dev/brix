/**
 * @file Native Message API
 * @description Toast/snackbar message system implementing MessageAPI from UIAdapter contract.
 * @module @brix/infra-adapter-ui-native/components/NativeMessage
 * @version 3.1.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Imperative API for showing toast messages
 * - Automatic dismissal with configurable duration
 * - Support for success, error, warning, info, and loading states
 */

import { createRoot, type Root } from 'react-dom/client';
import { createElement, type FC, type CSSProperties } from 'react';
import type { MessageAPI, MessageOptions, MessageDestroy, MessageType } from '@brix/runtime-sdk-api-web';
import { NativeIcon } from '../icons';

// ============================================================================
// Types
// ============================================================================

interface InternalMessage {
  id: string;
  type: MessageType;
  content: string | React.ReactNode;
  duration: number;
  closable: boolean;
  onClose?: () => void;
}

// ============================================================================
// Style Constants
// ============================================================================

/**
 * Message type colors and icons
 */
const MESSAGE_CONFIG: Record<MessageType, { icon: string; color: string; bgColor: string }> = {
  success: { icon: 'success', color: '#2e7d32', bgColor: '#e8f5e9' },
  error: { icon: 'error', color: '#d32f2f', bgColor: '#ffebee' },
  warning: { icon: 'warning', color: '#ed6c02', bgColor: '#fff3e0' },
  info: { icon: 'info', color: '#0288d1', bgColor: '#e3f2fd' },
  loading: { icon: 'pending', color: '#1976d2', bgColor: '#e3f2fd' },
};

// ============================================================================
// Message Container Component
// ============================================================================

interface MessageContainerProps {
  messages: InternalMessage[];
  onRemove: (id: string) => void;
}

/**
 * Message Container Component
 *
 * <p>Renders all active toast messages.</p>
 */
const MessageContainer: FC<MessageContainerProps> = ({ messages, onRemove }) => {
  // Container style (fixed at top center)
  const containerStyle: CSSProperties = {
    position: 'fixed',
    top: '16px',
    left: '50%',
    transform: 'translateX(-50%)',
    zIndex: 2000,
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
    pointerEvents: 'none',
  };

  return createElement(
    'div',
    { style: containerStyle },
    messages.map(msg => (
      createElement(MessageItem, {
        key: msg.id,
        message: msg,
        onRemove: () => onRemove(msg.id),
      })
    ))
  );
};

interface MessageItemProps {
  message: InternalMessage;
  onRemove: () => void;
}

/**
 * Single Message Item
 */
const MessageItem: FC<MessageItemProps> = ({ message, onRemove }) => {
  const config: { icon: string; color: string; bgColor: string } = 
    MESSAGE_CONFIG[message.type] ?? MESSAGE_CONFIG.info;

  // Message item style
  const itemStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    padding: '10px 16px',
    backgroundColor: config.bgColor,
    color: config.color,
    borderRadius: '4px',
    boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
    fontSize: '14px',
    pointerEvents: 'auto',
    animation: 'slideInDown 0.3s ease',
  };

  // Close button style
  const closeStyle: CSSProperties = {
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    padding: '2px',
    marginLeft: '8px',
    color: 'inherit',
    opacity: 0.6,
  };

  return createElement(
    'div',
    { style: itemStyle },
    [
      // Icon
      createElement(NativeIcon, {
        key: 'icon',
        name: config.icon,
        size: 'small',
        color: config.color,
      }),
      // Content
      createElement('span', { key: 'content' }, message.content),
      // Close button (if closable)
      message.closable && createElement(
        'button',
        {
          key: 'close',
          style: closeStyle,
          onClick: onRemove,
          'aria-label': 'Close message',
        },
        createElement(NativeIcon, { name: 'close', size: 'small' })
      ),
    ].filter(Boolean)
  );
};

// ============================================================================
// Message Manager (Singleton)
// ============================================================================

/**
 * Message Manager
 *
 * <p>Singleton class that manages toast message state and rendering.</p>
 */
class MessageManager {
  private messages: InternalMessage[] = [];
  private root: Root | null = null;
  private container: HTMLElement | null = null;
  private idCounter = 0;
  private timers: Map<string, ReturnType<typeof setTimeout>> = new Map();

  /**
   * Get or create container element and React root
   */
  private getRoot(): Root {
    if (typeof document === 'undefined') {
      throw new Error('Message API requires DOM environment');
    }

    if (!this.container) {
      this.container = document.createElement('div');
      this.container.id = 'native-message-root';
      document.body.appendChild(this.container);
    }

    if (!this.root) {
      this.root = createRoot(this.container);
    }

    return this.root;
  }

  /**
   * Re-render message container
   */
  private render() {
    const root = this.getRoot();
    root.render(
      createElement(MessageContainer, {
        messages: this.messages,
        onRemove: this.remove.bind(this),
      })
    );
  }

  /**
   * Add a new message
   */
  add(type: MessageType, options: MessageOptions | string): MessageDestroy {
    const id = `msg-${++this.idCounter}`;
    const opts = typeof options === 'string' ? { content: options } : options;

    const message: InternalMessage = {
      id,
      type,
      content: opts.content,
      duration: opts.duration ?? 3000,
      closable: opts.closable ?? false,
      onClose: opts.onClose,
    };

    // Use provided key or generated id
    const key = opts.key ?? id;

    // Remove existing message with same key
    if (opts.key) {
      this.remove(opts.key);
    }

    this.messages.push({ ...message, id: key });
    this.render();

    // Set auto-dismiss timer (if duration > 0)
    if (message.duration > 0) {
      const timer = setTimeout(() => {
        this.remove(key);
      }, message.duration);
      this.timers.set(key, timer);
    }

    // Return destroy function
    return () => this.remove(key);
  }

  /**
   * Remove a message by key/id
   */
  remove(key: string) {
    // Clear timer if exists
    const timer = this.timers.get(key);
    if (timer) {
      clearTimeout(timer);
      this.timers.delete(key);
    }

    // Find and remove message
    const index = this.messages.findIndex(m => m.id === key);
    if (index !== -1) {
      const removed = this.messages.splice(index, 1)[0];
      if (removed) {
        removed.onClose?.();
      }
      this.render();
    }
  }

  /**
   * Remove all messages
   */
  removeAll() {
    // Clear all timers
    this.timers.forEach(timer => clearTimeout(timer));
    this.timers.clear();

    // Call onClose for all messages
    this.messages.forEach(m => m.onClose?.());
    this.messages = [];
    this.render();
  }
}

// Singleton instance
const manager = new MessageManager();

// ============================================================================
// Message API Implementation
// ============================================================================

/**
 * Native Message API
 *
 * <p>Imperative API for displaying toast messages.
 * Implements MessageAPI interface from UIAdapter contract.</p>
 *
 * @example
 * ```typescript
 * // Show success message
 * nativeMessageAPI.success('File saved successfully!');
 *
 * // Show error with custom duration
 * nativeMessageAPI.error({ content: 'Upload failed', duration: 5000 });
 *
 * // Show loading and dismiss when done
 * const destroy = nativeMessageAPI.loading('Processing...');
 * await doAsyncWork();
 * destroy();
 * nativeMessageAPI.success('Done!');
 * ```
 */
export const nativeMessageAPI: MessageAPI = {
  success: (options: MessageOptions | string) => manager.add('success', options),
  error: (options: MessageOptions | string) => manager.add('error', options),
  warning: (options: MessageOptions | string) => manager.add('warning', options),
  info: (options: MessageOptions | string) => manager.add('info', options),
  loading: (options: MessageOptions | string) => manager.add('loading', typeof options === 'string' ? { content: options, duration: 0 } : { ...options, duration: 0 }),
  destroy: (key?: string) => { if (key) { manager.remove(key); } },
  destroyAll: () => manager.removeAll(),
};

export default nativeMessageAPI;
