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
 * @file infra-adapter-navigation-mobile Module Entry
 * @description Brix UI Mobile Navigation Adapter - React Navigation routing implementation
 * @module @brix-sdk/infra-adapter-navigation-mobile
 * @version 3.0.0
 * 
 * Module Description:
 * This module is the Mobile navigation adapter layer in the v3.0 Runtime Shell architecture.
 * It wraps React Navigation and provides a unified navigation capability interface.
 * 
 * Architecture Position:
 * - This module is an internal dependency of the Mobile Host layer
 * - Plugins should NOT use this module directly
 * - Plugins operate navigation through the NavigationCapability contract
 * 
 * v3.0 Boundary Constraints:
 * ? Plugins must NOT directly operate Navigation Container
 * ? Plugins must NOT access other plugins' navigation state
 * ? Plugins must NOT register global Deep Link handlers
 * ? Plugins declare navigation intent through NavigationCapability
 * ? Navigation permissions are controlled by Host
 * 
 * Usage (Host layer only):
 * ```typescript
 * import { RNNavigationAdapter, createScreenId } from '@brix-sdk/infra-adapter-navigation-mobile';
 * 
 * const adapter = new RNNavigationAdapter({
 *   screenRegistry: registry,
 *   initialScreenId: createScreenId('home', 'main'),
 * });
 * 
 * adapter.navigateTo('booking:detail', { bookingId: '123' });
 * ```
 */

export {
  RNNavigationAdapter,
  createScreenId,
  parseScreenId,
  type ScreenId,
  type ScreenMetadata,
  type ScreenOptions,
  type ScreenRegistry,
  type NavigationParams,
  type NavigationEvent,
  type NavigationState,
  type RNNavigationAdapterOptions,
} from './RNNavigationAdapter';

// ========== Version Info ==========
export const VERSION = '3.0.0';
