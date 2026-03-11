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
 * @file Message API Type Definitions
 * @description Defines types for the Toast/Snackbar Message API in the UI adapter system
 * @module @brix/runtime-sdk-api-web/types/ui/message
 * @version 3.2.0
 */

import type { ReactNode } from 'react';

/**
 * Message Type Variants
 */
export type MessageType = 'success' | 'error' | 'warning' | 'info' | 'loading';

/**
 * Message Configuration Options
 */
export interface MessageOptions {
  /**
   * Message Content
   */
  content: ReactNode;

  /**
   * Display Duration (ms)
   *
   * Duration before auto-dismiss. 0 means manual dismiss only.
   * @default 3000
   */
  duration?: number;

  /**
   * Closable
   *
   * When true, displays a close button.
   * @default false
   */
  closable?: boolean;

  /**
   * Unique Key
   *
   * Unique key for updating or closing specific messages.
   */
  key?: string;

  /**
   * Close Callback
   *
   * Callback fired when the message is closed.
   */
  onClose?: () => void;
}

/**
 * Message Destroy Function
 *
 * Function returned by message calls to manually dismiss the message.
 */
export type MessageDestroy = () => void;

/**
 * Message API Interface
 *
 * Imperative API for displaying toast/snackbar messages.
 * This is a stateless API - implementations manage message state internally.
 *
 * @example
 * ```tsx
 * // Using the message API from UIAdapter
 * const { message } = useUI();
 *
 * message.success({ content: 'Saved successfully!' });
 * message.error({ content: 'Operation failed', duration: 5000 });
 *
 * // With loading state
 * const destroy = message.loading({ content: 'Processing...' });
 * await doAsyncOperation();
 * destroy();
 * message.success({ content: 'Done!' });
 * ```
 */
export interface MessageAPI {
  /**
   * Success Message
   */
  success: (options: MessageOptions | string) => MessageDestroy;

  /**
   * Error Message
   */
  error: (options: MessageOptions | string) => MessageDestroy;

  /**
   * Warning Message
   */
  warning: (options: MessageOptions | string) => MessageDestroy;

  /**
   * Info Message
   */
  info: (options: MessageOptions | string) => MessageDestroy;

  /**
   * Loading Message
   */
  loading: (options: MessageOptions | string) => MessageDestroy;

  /**
   * Destroy Specific Message
   *
   * Destroys a message by its key.
   */
  destroy: (key?: string) => void;

  /**
   * Destroy All Messages
   */
  destroyAll: () => void;
}
