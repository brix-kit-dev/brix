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
 * @file infra-adapter-native-web Module Entry
 * @description Brix UI Native Module Adapter - Local plugin loading implementation based on dynamic import
 * @module @brix-sdk/infra-adapter-native-web
 * @version 3.0.0
 * 
 * ¡¾Module Description¡¿
 * This module is the simplest UI adapter implementation in the v3.0 Runtime Shell architecture.
 * Loads local modules based on browser native dynamic import.
 * 
 * ¡¾Applicable Scenarios¡¿
 * - Development Mode: Directly import components during local development
 * - Simple Deployment: Monolithic applications with all plugins bundled together
 * - SSR Scenarios: Module loading during server-side rendering
 * - Test Scenarios: Simulating plugin loading in unit tests
 * 
 * ¡¾Usage Example¡¿
 * ```typescript
 * import { NativePluginLoader } from '@brix-sdk/infra-adapter-native-web';
 * 
 * const loader = new NativePluginLoader();
 * 
 * const plugin = await loader.load({
 *   id: 'booking',
 *   name: 'Booking Management',
 *   version: '1.0.0',
 *   importFn: () => import('../plugins/booking/App'),
 * });
 * 
 * // Render plugin
 * <plugin.component />
 * ```
 */

export { 
  NativePluginLoader,
  NativePluginLoadError,
  type NativePluginManifest,
  type NativePluginInstance,
  type NativePluginStatus,
  type NativePluginLoaderOptions,
} from './NativePluginLoader';

// ========== Version Info ==========
export const VERSION = '3.0.0';
