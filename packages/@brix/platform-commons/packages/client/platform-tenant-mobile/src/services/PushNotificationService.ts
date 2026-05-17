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
 * @file PushNotificationService — Tenant-aware Push Notification Management
 * @description Manages push notification tenant routing: parses incoming
 * notification payloads for tenant context and handles auto-switch on notification tap.
 *
 * @module @brix-sdk/platform-tenant-mobile/services/PushNotificationService
 * @version 3.2.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — uses PushNotificationCapability from Layer 2A contract.
 * Does NOT directly import @react-native-firebase/messaging or similar native modules.
 *
 * [Push Notification Tenant Routing]
 * 1. Parse notification payload for tenant_id field
 * 2. On notification tap: auto-switch to target tenant if auto_switch=true
 * 3. Register push token with server for tenant-scoped delivery
 *
 * [Contract Constraint]
 * PushNotificationCapability provides: getToken, onMessage, onNotificationOpened,
 * getInitialNotification, displayLocalNotification.
 * Topic subscription (subscribeTopic/unsubscribeTopic) is managed server-side
 * via the push token registration API, not on the client.
 *
 * [Task #33 from v1.2 Design Document]
 * - Push token association with identity (not just device)
 * - Notification click auto-switches to target tenant
 *
 * @since 3.2.0
 */

import type {
  PushNotificationCapability,
  PushMessage,
  NotificationOpenedEvent,
  Subscription,
} from '@brix-sdk/runtime-sdk-api-mobile';
import {
  PUSH_TENANT_ID_KEY,
  PUSH_AUTO_SWITCH_KEY,
} from '../constants/MobileTenantConstants';
import type { PushTenantPayload } from '../types/MobileTenantTypes';

/**
 * Callback invoked when a tenant-scoped notification is tapped.
 *
 * @param tenantId the target tenant ID from the notification payload
 */
export type TenantNotificationTapHandler = (tenantId: string) => Promise<void>;

/**
 * PushNotificationService — Manages multi-tenant push notification lifecycle.
 *
 * Responsibilities:
 * - Parse incoming notifications for tenant routing metadata
 * - Handle notification tap to trigger tenant auto-switch
 * - Provide push token for server-side topic registration
 *
 * Note: FCM topic subscription is managed server-side. The client registers
 * its push token with the tenant API, and the server manages topic routing.
 *
 * @since 3.2.0
 */
export class PushNotificationService {
  private readonly pushCapability: PushNotificationCapability;
  private readonly subscriptions: Subscription[] = [];
  private tapHandler: TenantNotificationTapHandler | null = null;

  /**
   * Creates a new PushNotificationService instance.
   *
   * @param pushCapability the PushNotificationCapability from RuntimeContext
   */
  constructor(pushCapability: PushNotificationCapability) {
    this.pushCapability = pushCapability;
  }

  /**
   * Initialize the service. Must be called after the push capability is ready.
   *
   * Sets up listeners for:
   * - Notification opened events (for auto-switch)
   * - Foreground messages (for tenant-scoped local display)
   * - Initial notification (app launched from notification tap)
   *
   * @param onTenantNotificationTap callback invoked when user taps a tenant-scoped notification
   */
  async initialize(onTenantNotificationTap: TenantNotificationTapHandler): Promise<void> {
    this.tapHandler = onTenantNotificationTap;

    // Listen for notification opened events (foreground/background tap)
    const openedSub = this.pushCapability.onNotificationOpened((event: NotificationOpenedEvent) => {
      const payload = this.parseTenantPayload(event.notification);
      if (payload && payload.autoSwitch && this.tapHandler) {
        this.tapHandler(payload.tenantId);
      }
    });
    this.subscriptions.push(openedSub);

    // Listen for foreground messages to handle tenant-scoped notifications
    const messageSub = this.pushCapability.onMessage((message: PushMessage) => {
      const payload = this.parseTenantPayload(message);
      if (payload) {
        // Display a local notification with tenant context
        this.pushCapability.displayLocalNotification({
          id: message.messageId,
          title: message.title ?? '',
          body: message.body ?? '',
          data: message.data,
        });
      }
    });
    this.subscriptions.push(messageSub);

    // Check for initial notification (app cold-started from notification)
    const initialNotification = await this.pushCapability.getInitialNotification();
    if (initialNotification) {
      const payload = this.parseTenantPayload(initialNotification);
      if (payload && payload.autoSwitch && this.tapHandler) {
        await this.tapHandler(payload.tenantId);
      }
    }
  }

  /**
   * Get the current push token for server-side registration.
   *
   * The server uses this token to manage FCM topic subscriptions
   * for all tenants the user belongs to.
   *
   * @returns the FCM/APNs push token
   */
  async getPushToken(): Promise<string> {
    return this.pushCapability.getToken();
  }

  /**
   * Register the push token with the given tenant IDs via server API.
   *
   * This is a client-side convenience method that delegates to the
   * MobileTenantRepository for server-side topic management.
   *
   * @param tenantIds the tenant IDs to register the push token for
   * @param registerFn async function that sends the token to the server
   */
  async registerTokenForTenants(
    tenantIds: readonly string[],
    registerFn: (token: string, tenantIds: readonly string[]) => Promise<void>,
  ): Promise<void> {
    const token = await this.pushCapability.getToken();
    await registerFn(token, tenantIds);
  }

  /**
   * Parse tenant routing metadata from a push notification payload.
   *
   * Looks for the 'tenant_id' field in the notification's data payload.
   * Returns null if the notification has no tenant routing information.
   *
   * @param message the push notification message
   * @returns parsed tenant payload, or null if not tenant-scoped
   */
  parseTenantPayload(message: PushMessage): PushTenantPayload | null {
    const data = message.data;
    if (!data) return null;

    const tenantId = data[PUSH_TENANT_ID_KEY];
    if (typeof tenantId !== 'string' || !tenantId) return null;

    const autoSwitch = data[PUSH_AUTO_SWITCH_KEY] === true
      || data[PUSH_AUTO_SWITCH_KEY] === 'true';

    return { tenantId, autoSwitch };
  }

  /**
   * Destroy the service and clean up all subscriptions.
   *
   * Must be called when the tenant module is unmounted.
   */
  destroy(): void {
    this.tapHandler = null;

    for (const sub of this.subscriptions) {
      sub.unsubscribe();
    }
    this.subscriptions.length = 0;
  }
}
