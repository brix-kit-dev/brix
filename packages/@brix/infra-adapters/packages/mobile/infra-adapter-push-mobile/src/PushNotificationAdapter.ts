/**
 * @file Push Notification Adapter
 * @description Brix UI Mobile push notification capability wrapper - FCM/APNs unified interface
 * @module @brix/infra-adapter-push-mobile
 * @version 3.0.0
 * 
 * Design Notes:
 * This adapter is the Mobile push notification capability layer of the v3.0 Runtime Shell architecture.
 * It wraps FCM (Android) and APNs (iOS), providing a unified push interface.
 * 
 * v3.0 Architecture Position:
 * ```
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    Mobile Plugin Layer                      │
 * │    ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
 * │    │  Booking    │  │  Products   │  │  Messenger  │       │
 * │    │  Plugin     │  │  Plugin     │  │  Plugin     │       │
 * │    └──────┬──────┘  └──────┬──────┘  └──────┬──────┘       │
 * │           │                │                │              │
 * │           ▼                ▼                ▼              │
 * │    ┌─────────────────────────────────────────────────┐     │
 * │    │        PushCapability Contract Interface         │     │
 * │    │  - onMessage(handler)                           │     │
 * │    │  - subscribeTopic(topic)                        │     │
 * │    │  - getToken()                                   │     │
 * │    └─────────────────────────────────────────────────┘     │
 * │                           │                                │
 * │                           ▼                                │
 * │    ┌─────────────────────────────────────────────────┐     │
 * │    │      PushNotificationAdapter (this adapter)      │     │
 * │    │  - Token management                              │     │
 * │    │  - Message routing and dispatching              │     │
 * │    │  - Notification permission management           │     │
 * │    └─────────────────────────────────────────────────┘     │
 * │                           │                                │
 * │            ┌──────────────┴──────────────┐                 │
 * │            ▼                             ▼                 │
 * │    ┌──────────────────┐         ┌──────────────────┐      │
 * │    │   FCM (Android)  │         │   APNs (iOS)     │      │
 * │    └──────────────────┘         └──────────────────┘      │
 * └─────────────────────────────────────────────────────────────┘
 * ```
 * 
 * Push Message Types:
 * - Notification: Displayed in system notification bar
 * - Data: Silent push, handled in app
 * - Mixed: Notification + data combined
 * 
 * Message Routing Mechanism:
 * - Push messages are routed to specified plugin via `targetPluginId` field
 * - Messages without `targetPluginId` are handled by Host
 * - Plugins can only receive messages belonging to them
 * 
 * v3.0 Boundary Constraints:
 * ❌ Plugins must NOT directly access FCM/APNs SDK
 * ❌ Plugins must NOT listen to other plugins' push messages
 * ❌ Plugins must NOT modify global push configuration
 * ✅ Plugins subscribe to messages through PushCapability
 * ✅ Token is managed by Host
 * 
 * Usage Example (Host layer only):
 * ```typescript
 * import { PushNotificationAdapter } from '@brix/infra-adapter-push-mobile';
 * 
 * const adapter = new PushNotificationAdapter({
 *   onTokenRefresh: (token) => {
 *     // Report new Token to server
 *   },
 * });
 * 
 * // Initialize push
 * await adapter.initialize();
 * 
 * // Listen to messages
 * adapter.onMessage((message) => {
 *   routeMessageToPlugin(message);
 * });
 * ```
 */

// ========== Type Definitions ==========

/**
 * Push message type
 */
export type PushMessageType = 'notification' | 'data' | 'mixed';

/**
 * Notification priority
 */
export type NotificationPriority = 'default' | 'low' | 'high' | 'max';

/**
 * Push message data
 */
export interface PushMessageData {
  /** Message ID */
  messageId: string;
  /** Message type */
  type: PushMessageType;
  /** Notification title */
  title?: string;
  /** Notification body */
  body?: string;
  /** Custom data */
  data?: Record<string, string>;
  /** Target plugin ID (for message routing) */
  targetPluginId?: string;
  /** Notification icon (Android) */
  icon?: string;
  /** Notification sound */
  sound?: string;
  /** Badge number */
  badge?: number;
  /** Priority */
  priority?: NotificationPriority;
  /** Message sent time */
  sentTime?: number;
  /** Message source Topic */
  topic?: string;
}

/**
 * Push Token information
 */
export interface PushToken {
  /** Token value */
  token: string;
  /** Token type */
  type: 'fcm' | 'apns';
  /** Obtained time */
  obtainedAt: number;
}

/**
 * Notification permission status
 */
export interface NotificationPermissionStatus {
  /** Whether authorized */
  authorized: boolean;
  /** iOS authorization status */
  authorizationStatus?: 'authorized' | 'denied' | 'notDetermined' | 'provisional';
  /** Whether sound enabled */
  soundEnabled?: boolean;
  /** Whether badge enabled */
  badgeEnabled?: boolean;
  /** Whether notification bar enabled */
  alertEnabled?: boolean;
  /** Whether lock screen display enabled */
  lockScreenEnabled?: boolean;
}

/**
 * Local notification configuration
 */
export interface LocalNotificationConfig {
  /** Notification ID */
  id: string;
  /** Title */
  title: string;
  /** Body */
  body: string;
  /** Custom data */
  data?: Record<string, string>;
  /** Target plugin ID */
  targetPluginId?: string;
  /** Scheduled trigger time (timestamp) */
  fireDate?: number;
  /** Repeat interval */
  repeatInterval?: 'minute' | 'hour' | 'day' | 'week';
  /** Sound */
  sound?: string;
  /** Badge */
  badge?: number;
  /** Android notification channel */
  channelId?: string;
}

/**
 * Message handler
 */
export type PushMessageHandler = (message: PushMessageData) => void;

/**
 * Token refresh handler
 */
export type TokenRefreshHandler = (token: PushToken) => void;

/**
 * Notification click handler
 */
export type NotificationOpenHandler = (message: PushMessageData) => void;

/**
 * PushNotificationAdapter configuration options
 */
export interface PushNotificationAdapterOptions {
  /** Token refresh callback */
  onTokenRefresh?: TokenRefreshHandler;
  /** Notification click callback */
  onNotificationOpen?: NotificationOpenHandler;
  /** Whether to auto request permission */
  autoRequestPermission?: boolean;
  /** Android default notification channel ID */
  defaultChannelId?: string;
}

// ========== Core Implementation ==========

/**
 * Push Notification Adapter
 * 
 * Responsibilities:
 * - Wrap FCM/APNs push capabilities
 * - Manage push Token
 * - Route push messages to plugins
 * - Manage notification permissions
 * 
 * Internal Implementation:
 * - Unify Android/iOS push interface
 * - Messages are routed via targetPluginId
 * - Token auto-refresh and report
 * 
 * @example
 * ```typescript
 * const adapter = new PushNotificationAdapter({
 *   onTokenRefresh: async (token) => {
 *     await api.registerPushToken(token);
 *   },
 *   onNotificationOpen: (message) => {
 *     navigation.navigate(message.data?.screen);
 *   },
 * });
 * 
 * await adapter.initialize();
 * 
 * // Listen to foreground messages
 * adapter.onMessage((message) => {
 *   if (message.targetPluginId) {
 *     pluginMessageBus.dispatch(message.targetPluginId, message);
 *   }
 * });
 * ```
 */
export class PushNotificationAdapter {
  /** Configuration options */
  private readonly options: PushNotificationAdapterOptions;
  
  /** Message handlers list */
  private readonly messageHandlers: Set<PushMessageHandler> = new Set();
  
  /** Current Token */
  private currentToken: PushToken | null = null;
  
  /** Whether initialized */
  private initialized = false;

  /**
   * Create PushNotificationAdapter instance
   * 
   * @param options - Adapter configuration
   */
  constructor(options: PushNotificationAdapterOptions = {}) {
    this.options = options;
  }

  /**
   * Initialize push service
   * 
   * Initialization Flow:
   * 1. Check/request notification permission
   * 2. Register push service
   * 3. Get Token
   * 4. Setup message listeners
   * 
   * @returns Whether initialization succeeded
   * 
   * @example
   * ```typescript
   * const success = await adapter.initialize();
   * if (success) {
   *   console.log('Push initialized');
   * }
   * ```
   */
  async initialize(): Promise<boolean> {
    if (this.initialized) {
      return true;
    }

    try {
      // 1. Check/request permission
      if (this.options.autoRequestPermission !== false) {
        const permission = await this.requestPermission();
        if (!permission.authorized) {
          console.warn('[PushNotificationAdapter] Notification permission denied');
          return false;
        }
      }

      // 2. Get Token (requires Native implementation)
      // this.currentToken = await this.getTokenInternal();

      // 3. Setup listeners (requires Native implementation)
      // this.setupListeners();

      this.initialized = true;
      
      throw new Error(
        `[PushNotificationAdapter] Initialization requires Native integration.`
      );
    } catch (error) {
      console.error('[PushNotificationAdapter] Initialization failed:', error);
      return false;
    }
  }

  /**
   * Get current push Token
   * 
   * @returns Push Token or null
   */
  async getToken(): Promise<PushToken | null> {
    if (!this.initialized) {
      console.warn('[PushNotificationAdapter] Not initialized');
      return null;
    }

    // If there's a cached Token, return directly
    if (this.currentToken) {
      return this.currentToken;
    }

    throw new Error(
      `[PushNotificationAdapter] Token retrieval requires Native integration.`
    );
  }

  /**
   * Request notification permission
   * 
   * @returns Permission status
   */
  async requestPermission(): Promise<NotificationPermissionStatus> {
    throw new Error(
      `[PushNotificationAdapter] Permission request requires Native integration.`
    );
  }

  /**
   * Get current notification permission status
   * 
   * @returns Permission status
   */
  async getPermissionStatus(): Promise<NotificationPermissionStatus> {
    throw new Error(
      `[PushNotificationAdapter] Permission check requires Native integration.`
    );
  }

  /**
   * Add message handler
   * 
   * @param handler - Message handler function
   * @returns Unsubscribe function
   * 
   * @example
   * ```typescript
   * const unsubscribe = adapter.onMessage((message) => {
   *   console.log('Received:', message);
   * });
   * 
   * // Unsubscribe
   * unsubscribe();
   * ```
   */
  onMessage(handler: PushMessageHandler): () => void {
    this.messageHandlers.add(handler);
    return () => this.messageHandlers.delete(handler);
  }

  /**
   * Subscribe to Topic
   * 
   * @param topic - Topic name
   * 
   * @example
   * ```typescript
   * await adapter.subscribeTopic('news');
   * ```
   */
  async subscribeTopic(topic: string): Promise<void> {
    throw new Error(
      `[PushNotificationAdapter] Topic subscription requires Native integration. ` +
      `Topic: ${topic}`
    );
  }

  /**
   * Unsubscribe from Topic
   * 
   * @param topic - Topic name
   */
  async unsubscribeTopic(topic: string): Promise<void> {
    throw new Error(
      `[PushNotificationAdapter] Topic unsubscription requires Native integration. ` +
      `Topic: ${topic}`
    );
  }

  /**
   * Schedule local notification
   * 
   * @param config - Local notification configuration
   * 
   * @example
   * ```typescript
   * await adapter.scheduleLocalNotification({
   *   id: 'reminder-1',
   *   title: 'Reminder',
   *   body: 'You have a pending booking',
   *   fireDate: Date.now() + 3600000, // 1 hour later
   * });
   * ```
   */
  async scheduleLocalNotification(_config: LocalNotificationConfig): Promise<void> {
    throw new Error(
      `[PushNotificationAdapter] Local notification requires Native integration.`
    );
  }

  /**
   * Cancel local notification
   * 
   * @param notificationId - Notification ID
   */
  async cancelLocalNotification(notificationId: string): Promise<void> {
    throw new Error(
      `[PushNotificationAdapter] Cancel notification requires Native integration. ` +
      `ID: ${notificationId}`
    );
  }

  /**
   * Cancel all local notifications
   */
  async cancelAllLocalNotifications(): Promise<void> {
    throw new Error(
      `[PushNotificationAdapter] Cancel all notifications requires Native integration.`
    );
  }

  /**
   * Set app badge number
   * 
   * @param count - Badge number (0 to clear)
   */
  async setBadgeCount(count: number): Promise<void> {
    throw new Error(
      `[PushNotificationAdapter] Badge setting requires Native integration. ` +
      `Count: ${count}`
    );
  }

  /**
   * Get app badge number
   * 
   * @returns Current badge number
   */
  async getBadgeCount(): Promise<number> {
    throw new Error(
      `[PushNotificationAdapter] Badge retrieval requires Native integration.`
    );
  }

  // ========== Private Methods ==========

  /**
   * Dispatch message to handlers
   * 
   * Note: This method is for the Native layer to call when receiving push messages.
   * Not used directly in JavaScript environment, but method signature is retained
   * for React Native bridging use.
   * 
   * @internal
   */
  protected _dispatchMessage(message: PushMessageData): void {
    this.messageHandlers.forEach((handler) => {
      try {
        handler(message);
      } catch (error) {
        console.error('[PushNotificationAdapter] Handler error:', error);
      }
    });
  }
}

// ========== Helper Functions ==========

/**
 * Create message route key
 * 
 * @param pluginId - Plugin ID
 * @param messageType - Message type
 * @returns Route key
 */
export function createMessageRouteKey(pluginId: string, messageType?: string): string {
  return messageType ? `${pluginId}:${messageType}` : pluginId;
}

/**
 * Check if message belongs to specified plugin
 * 
 * @param message - Push message
 * @param pluginId - Plugin ID
 * @returns Whether belongs to the plugin
 */
export function isMessageForPlugin(message: PushMessageData, pluginId: string): boolean {
  return message.targetPluginId === pluginId;
}
