/**
 * @file Push notification capability type definitions
 * @description Define push notification capability contract
 * @module @brix/runtime-sdk-api-mobile/types/push-notification
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Mobile-specific capability, provides push notification functionality.
 *
 * [Architecture Notes]
 * - Supports remote push (FCM/APNs)
 * - Supports local notifications
 * - Unified message handling interface
 */

import type { Subscription } from './common';

// =========================================
// Push Notification Capability Type Identifier
// =========================================

/**
 * Push Notification Capability Type Identifier
 */
export const PushNotificationCapabilityType = Symbol.for('PushNotificationCapability');

// =========================================
// Push Message Types
// =========================================

/**
 * Push Message
 */
export interface PushMessage {
  /** Message ID */
  readonly messageId: string;
  /** Notification title */
  readonly title?: string;
  /** Notification body */
  readonly body?: string;
  /** Data payload */
  readonly data?: Record<string, unknown>;
  /** Sent timestamp */
  readonly sentTime?: number;
  /** Custom fields */
  readonly [key: string]: unknown;
}

/**
 * Notification Presentation Options
 */
export type NotificationPresentationOptions = {
  /** Whether to show alert */
  readonly alert?: boolean;
  /** Whether to show badge */
  readonly badge?: boolean;
  /** Whether to play sound */
  readonly sound?: boolean;
};

/**
 * Notification Opened Event
 */
export interface NotificationOpenedEvent {
  /** Triggered notification */
  readonly notification: PushMessage;
  /** User clicked action (if any) */
  readonly action?: string;
}

// =========================================
// Local Notification
// =========================================

/**
 * Local Notification Configuration
 */
export interface LocalNotification {
  /** Notification ID */
  readonly id: string;
  /** Notification title */
  readonly title: string;
  /** Notification body */
  readonly body: string;
  /** Trigger time (Date or cron expression) */
  readonly trigger?: LocalNotificationTrigger;
  /** Data payload */
  readonly data?: Record<string, unknown>;
  /** iOS badge count */
  readonly badge?: number;
  /** Sound file name */
  readonly sound?: string;
  /** Android channel ID */
  readonly channelId?: string;
  /** Notification icon (Android) */
  readonly smallIcon?: string;
  /** Large icon (Android) */
  readonly largeIcon?: string;
}

/**
 * Local Notification Trigger
 */
export type LocalNotificationTrigger =
  | { type: 'date'; date: Date }
  | { type: 'interval'; seconds: number; repeats?: boolean }
  | { type: 'calendar'; hour: number; minute: number; repeats?: boolean };

// =========================================
// Push Permission
// =========================================

/**
 * Push Permission Status
 */
export type PushPermissionStatus =
  | 'granted'       // Granted
  | 'denied'        // Denied
  | 'undetermined'  // Undetermined
  | 'provisional';  // Provisional authorization (iOS)

/**
 * Push Permission Request Options
 */
export interface PushPermissionOptions {
  /** Request alert permission */
  readonly alert?: boolean;
  /** Request badge permission */
  readonly badge?: boolean;
  /** Request sound permission */
  readonly sound?: boolean;
  /** Request provisional authorization (iOS 12+) */
  readonly provisional?: boolean;
  /** Request critical alert permission (iOS) */
  readonly criticalAlert?: boolean;
}

// =========================================
// Push Notification Capability Contract
// =========================================

/**
 * Push Notification Capability Contract
 *
 * <p>Provides push notification capability for plugins.</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const push = context.getCapability<PushNotificationCapability>(PushNotificationCapabilityType);
 * 
 * // Request permission
 * const granted = await push.requestPermission();
 * 
 * if (granted) {
 *   // Get push token
 *   const token = await push.getToken();
 *   console.log('Push Token:', token);
 *   
 *   // Listen for messages
 *   const subscription = push.onMessage((message) => {
 *     console.log('Received push:', message);
 *   });
 *   
 *   // Listen for token refresh
 *   push.onTokenRefresh((newToken) => {
 *     console.log('Token refreshed:', newToken);
 *   });
 * }
 * ```
 */
export interface PushNotificationCapability {
  /**
   * Get push token
   *
   * @returns FCM/APNs push token
   */
  getToken(): Promise<string>;

  /**
   * Request push permission
   *
   * @param options Permission request options
   * @returns Whether permission granted
   */
  requestPermission(options?: PushPermissionOptions): Promise<boolean>;

  /**
   * Get permission status
   *
   * @returns Permission status
   */
  getPermissionStatus(): Promise<PushPermissionStatus>;

  /**
   * Listen for foreground push messages
   *
   * <p>Triggered when push message is received while app is in foreground.</p>
   *
   * @param callback Message handler callback
   * @returns Subscription object
   */
  onMessage(callback: (message: PushMessage) => void): Subscription;

  /**
   * Listen for token refresh
   *
   * @param callback Token refresh callback
   * @returns Subscription object
   */
  onTokenRefresh(callback: (token: string) => void): Subscription;

  /**
   * Listen for notification opened event
   *
   * <p>Triggered when user opens app by tapping notification.</p>
   *
   * @param callback Notification opened callback
   * @returns Subscription object
   */
  onNotificationOpened(callback: (event: NotificationOpenedEvent) => void): Subscription;

  /**
   * Get initial notification
   *
   * <p>When app is launched from notification, get the launching notification info.</p>
   *
   * @returns Initial notification, null if none
   */
  getInitialNotification(): Promise<PushMessage | null>;

  /**
   * Display local notification
   *
   * @param notification Local notification configuration
   */
  displayLocalNotification(notification: LocalNotification): Promise<void>;

  /**
   * Cancel local notification
   *
   * @param notificationId Notification ID
   */
  cancelLocalNotification(notificationId: string): Promise<void>;

  /**
   * Cancel all local notifications
   */
  cancelAllLocalNotifications(): Promise<void>;

  /**
   * Set app badge count (iOS)
   *
   * @param count Badge count
   */
  setBadgeCount(count: number): Promise<void>;

  /**
   * Get app badge count (iOS)
   *
   * @returns Badge count
   */
  getBadgeCount(): Promise<number>;
}
