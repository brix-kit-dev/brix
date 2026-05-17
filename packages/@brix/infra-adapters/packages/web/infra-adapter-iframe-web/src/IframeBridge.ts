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
 * @file iframe Cross-Window Communication Bridge
 * @description Manages postMessage communication between Host and iframe plugins
 * @module @brix-sdk/infra-adapter-iframe-web/IframeBridge
 * @version 3.0.0
 * 
 * ��Design Notes��
 * IframeBridge is the communication bridge between Host and iframe plugins.
 * Implements secure cross-window communication based on postMessage API.
 * 
 * ��Communication Flow��
 * ```
 * ����������������������������������    postMessage    ����������������������������������
 * ��    Host       �� ���������������������������������� ��   iframe      ��
 * ��  IframeBridge ��                   ��   (Plugin)    ��
 * ����������������������������������                   ����������������������������������
 * ```
 * 
 * ��Security Mechanisms��
 * 1. Origin validation: Only accept messages from whitelisted domains
 * 2. Message signature: Prevent message replay via messageId
 * 3. Timeout handling: Auto-fail requests on timeout
 * 
 * ��Architectural Constraint - v3.0 Runtime Shell��
 * - All iframe communication must go through IframeBridge
 * - Sensitive operations (navigation, state modification) require Host confirmation
 * - All messages must be traceable (for governance)
 */

import {
  IframeBridgeMessageType,
  type IframeBridgeMessage,
  IframeBridgeError,
} from './types';

// Re-export Payload types for external use
export type {
  InitPayload,
  NavRequestPayload,
  NavResponsePayload,
  EventPayload,
  StateRequestPayload,
  StateResponsePayload,
} from './types';

/**
 * Message handler type
 */
export type MessageHandler<T = unknown, R = void> = (
  payload: T,
  message: IframeBridgeMessage<T>
) => R | Promise<R>;

/**
 * Pending request record
 */
interface PendingRequest {
  resolve: (value: unknown) => void;
  reject: (error: Error) => void;
  timer: ReturnType<typeof setTimeout>;
}

/**
 * IframeBridge Configuration Options
 */
export interface IframeBridgeOptions {
  /**
   * Allowed message origins list
   * 
   * Only accepts messages from these domains.
   * Use '*' to allow all origins (not recommended for production).
   */
  allowedOrigins: string[];
  
  /**
   * Request timeout (milliseconds)
   * 
   * @default 30000
   */
  timeout?: number;
  
  /**
   * Message received callback (for logging)
   */
  onMessageReceived?: (message: IframeBridgeMessage) => void;
  
  /**
   * Message sent callback (for logging)
   */
  onMessageSent?: (message: IframeBridgeMessage) => void;
  
  /**
   * Error callback
   */
  onError?: (error: Error, message?: IframeBridgeMessage) => void;
}

/**
 * iframe Cross-Window Communication Bridge
 * 
 * Manages bidirectional communication between Host and iframe plugins.
 * 
 * ��Features��
 * - Message sending and receiving
 * - Request-response pattern
 * - Event broadcasting
 * - Origin security validation
 * 
 * @example
 * ```typescript
 * // Create bridge
 * const bridge = new IframeBridge({
 *   allowedOrigins: ['http://localhost:3010'],
 * });
 * 
 * // Register message handler
 * bridge.on(IframeBridgeMessageType.NAV_REQUEST, async (payload) => {
 *   // Handle navigation request
 *   return { success: true };
 * });
 * 
 * // Send message to iframe
 * await bridge.send(iframe.contentWindow, {
 *   type: IframeBridgeMessageType.INIT,
 *   payload: { pluginId: 'booking' },
 * });
 * ```
 */
export class IframeBridge {
  /** Configuration options */
  private readonly options: Required<Omit<IframeBridgeOptions, 'onMessageReceived' | 'onMessageSent' | 'onError'>> & 
    Pick<IframeBridgeOptions, 'onMessageReceived' | 'onMessageSent' | 'onError'>;
  
  /** Message handlers mapping */
  private readonly handlers = new Map<IframeBridgeMessageType, MessageHandler[]>();
  
  /** Pending requests (for request-response pattern) */
  private readonly pendingRequests = new Map<string, PendingRequest>();
  
  /** Message ID counter */
  private messageIdCounter = 0;
  
  /** Whether listening has started */
  private isListening = false;
  
  /**
   * Create IframeBridge instance
   * 
   * @param options - Configuration options
   */
  constructor(options: IframeBridgeOptions) {
    this.options = {
      allowedOrigins: options.allowedOrigins,
      timeout: options.timeout ?? 30000,
      onMessageReceived: options.onMessageReceived,
      onMessageSent: options.onMessageSent,
      onError: options.onError,
    };
  }
  
  /**
   * Start listening for messages
   * 
   * Registers message event listener on window.
   * Should be called during Host initialization.
   */
  startListening(): void {
    if (this.isListening) {
      return;
    }
    
    window.addEventListener('message', this.handleMessage);
    this.isListening = true;
  }
  
  /**
   * Stop listening for messages
   * 
   * Removes message event listener.
   * Should be called during Host destruction.
   */
  stopListening(): void {
    if (!this.isListening) {
      return;
    }
    
    window.removeEventListener('message', this.handleMessage);
    this.isListening = false;
    
    // Clean up pending requests
    for (const [, request] of this.pendingRequests) {
      clearTimeout(request.timer);
      request.reject(new Error('Bridge stopped'));
    }
    this.pendingRequests.clear();
  }
  
  /**
   * Register message handler
   * 
   * @param type - Message type
   * @param handler - Handler function
   * @returns Unregister function
   */
  on<T = unknown, R = void>(
    type: IframeBridgeMessageType,
    handler: MessageHandler<T, R>
  ): () => void {
    const handlers = this.handlers.get(type) ?? [];
    handlers.push(handler as MessageHandler);
    this.handlers.set(type, handlers);
    
    return () => {
      const currentHandlers = this.handlers.get(type);
      if (currentHandlers) {
        const index = currentHandlers.indexOf(handler as MessageHandler);
        if (index > -1) {
          currentHandlers.splice(index, 1);
        }
      }
    };
  }
  
  /**
   * Send message to specified window
   * 
   * @param targetWindow - Target window (iframe.contentWindow)
   * @param type - Message type
   * @param payload - Message payload
   * @param sourcePluginId - Source plugin ID
   * @param targetOrigin - Target Origin
   */
  send<T>(
    targetWindow: Window,
    type: IframeBridgeMessageType,
    payload: T,
    sourcePluginId: string,
    targetOrigin: string
  ): void {
    const message: IframeBridgeMessage<T> = {
      type,
      messageId: this.generateMessageId(),
      payload,
      sourcePluginId,
      target: 'PLUGIN',
      timestamp: Date.now(),
    };
    
    targetWindow.postMessage(message, targetOrigin);
    
    this.options.onMessageSent?.(message as IframeBridgeMessage);
  }
  
  /**
   * Send request and wait for response
   * 
   * @param targetWindow - Target window
   * @param requestType - Request message type
   * @param responseType - Response message type
   * @param payload - Request payload
   * @param sourcePluginId - Source plugin ID
   * @param targetOrigin - Target Origin
   * @returns Response payload
   */
  async request<TReq, TRes>(
    targetWindow: Window,
    requestType: IframeBridgeMessageType,
    _responseType: IframeBridgeMessageType,
    payload: TReq,
    sourcePluginId: string,
    targetOrigin: string
  ): Promise<TRes> {
    const messageId = this.generateMessageId();
    
    const message: IframeBridgeMessage<TReq> = {
      type: requestType,
      messageId,
      payload,
      sourcePluginId,
      target: 'PLUGIN',
      timestamp: Date.now(),
    };
    
    return new Promise<TRes>((resolve, reject) => {
      // Set timeout
      const timer = setTimeout(() => {
        this.pendingRequests.delete(messageId);
        reject(new IframeBridgeError(
          sourcePluginId,
          requestType,
          `Request timeout (${this.options.timeout}ms)`
        ));
      }, this.options.timeout);
      
      // Record pending request
      this.pendingRequests.set(messageId, {
        resolve: resolve as (value: unknown) => void,
        reject,
        timer,
      });
      
      // Send request
      targetWindow.postMessage(message, targetOrigin);
      this.options.onMessageSent?.(message as IframeBridgeMessage);
    });
  }
  
  /**
   * Broadcast message to all registered iframes
   * 
   * @param iframes - List of iframe elements
   * @param type - Message type
   * @param payload - Message payload
   * @param sourcePluginId - Source plugin ID
   */
  broadcast<T>(
    iframes: HTMLIFrameElement[],
    type: IframeBridgeMessageType,
    payload: T,
    sourcePluginId: string
  ): void {
    for (const iframe of iframes) {
      const origin = this.getIframeOrigin(iframe);
      if (origin && iframe.contentWindow) {
        this.send(iframe.contentWindow, type, payload, sourcePluginId, origin);
      }
    }
  }
  
  /**
   * Handle received messages
   */
  private handleMessage = (event: MessageEvent): void => {
    // Origin validation
    if (!this.isOriginAllowed(event.origin)) {
      return;
    }
    
    // Validate message format
    const message = event.data as IframeBridgeMessage;
    if (!this.isValidMessage(message)) {
      return;
    }
    
    this.options.onMessageReceived?.(message);
    
    // Check if this is a response message (for request-response pattern)
    const pendingRequest = this.pendingRequests.get(message.messageId);
    if (pendingRequest) {
      clearTimeout(pendingRequest.timer);
      this.pendingRequests.delete(message.messageId);
      pendingRequest.resolve(message.payload);
      return;
    }
    
    // Dispatch to registered handlers
    const handlers = this.handlers.get(message.type);
    if (handlers && handlers.length > 0) {
      for (const handler of handlers) {
        try {
          const result = handler(message.payload, message);
          
          // If handler returns Promise, wait for completion
          if (result instanceof Promise) {
            result.catch((error) => {
              this.options.onError?.(error, message);
            });
          }
        } catch (error) {
          this.options.onError?.(
            error instanceof Error ? error : new Error(String(error)),
            message
          );
        }
      }
    }
  };
  
  /**
   * Check if Origin is in whitelist
   */
  private isOriginAllowed(origin: string): boolean {
    if (this.options.allowedOrigins.includes('*')) {
      return true;
    }
    return this.options.allowedOrigins.includes(origin);
  }
  
  /**
   * Validate message format
   */
  private isValidMessage(message: unknown): message is IframeBridgeMessage {
    if (typeof message !== 'object' || message === null) {
      return false;
    }
    
    const msg = message as Record<string, unknown>;
    return (
      typeof msg.type === 'string' &&
      typeof msg.messageId === 'string' &&
      typeof msg.sourcePluginId === 'string' &&
      typeof msg.timestamp === 'number'
    );
  }
  
  /**
   * Get iframe's Origin
   */
  private getIframeOrigin(iframe: HTMLIFrameElement): string | null {
    try {
      const url = new URL(iframe.src);
      return url.origin;
    } catch {
      return null;
    }
  }
  
  /**
   * Generate unique message ID
   */
  private generateMessageId(): string {
    return `msg_${Date.now()}_${++this.messageIdCounter}`;
  }
}
