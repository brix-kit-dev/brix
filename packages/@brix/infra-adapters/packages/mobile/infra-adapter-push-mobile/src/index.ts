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
 * @file infra-adapter-push-mobile Module Entry
 * @description Brix UI Mobile Push Adapter - FCM/APNs push notification capability wrapper
 * @module @brix-sdk/infra-adapter-push-mobile
 * @version 3.0.0
 * 
 * Module Description:
 * This module is the Mobile push adapter layer in the v3.0 Runtime Shell architecture.
 * It wraps FCM (Android) and APNs (iOS), providing a unified push interface.
 * 
 * Architecture Position:
 * - This module is an internal dependency of the Mobile Host layer
 * - Plugins should NOT use this module directly
 * - Plugins subscribe to messages through the PushCapability contract
 * 
 * v3.0 Boundary Constraints:
 * ? Plugins must NOT directly access FCM/APNs SDK
 * ? Plugins must NOT listen to other plugins' push messages
 * ? Plugins must NOT modify global push configuration
 * ? Plugins subscribe to messages through PushCapability
 * ? Token is managed by Host
 * 
 * Usage (Host layer only):
 * ```typescript
 * import { PushNotificationAdapter } from '@brix-sdk/infra-adapter-push-mobile';
 * 
 * const adapter = new PushNotificationAdapter({
 *   onTokenRefresh: (token) => api.registerToken(token),
 * });
 * 
 * await adapter.initialize();
 * ```
 */

export {
  PushNotificationAdapter,
  createMessageRouteKey,
  isMessageForPlugin,
  type PushMessageType,
  type NotificationPriority,
  type PushMessageData,
  type PushToken,
  type NotificationPermissionStatus,
  type LocalNotificationConfig,
  type PushMessageHandler,
  type TokenRefreshHandler,
  type NotificationOpenHandler,
  type PushNotificationAdapterOptions,
} from './PushNotificationAdapter';

// ========== Version Info ==========
export const VERSION = '3.0.0';
