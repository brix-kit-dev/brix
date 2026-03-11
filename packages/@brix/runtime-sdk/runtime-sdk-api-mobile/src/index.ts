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
 * @file @brix/runtime-sdk-api-mobile unified entry
 * @description UI capability contract definitions - React Native mobile platform (framework-agnostic)
 * @module @brix/runtime-sdk-api-mobile
 * @version 3.2.0
 *
 * [Module Responsibilities]
 * Define mobile UI runtime capability contracts for plugins to obtain and use via RuntimeContext.
 *
 * [Capability Categories]
 * - Navigation Capability: Page navigation, router management
 * - Authentication Capability (Auth): User identity, permission validation
 * - State Capability: Plugin state management
 * - Event Capability (EventBus): Cross-plugin communication
 * - Config Capability: Runtime configuration reading
 * - HTTP Capability: Unified HTTP requests
 * 
 * [Mobile-Specific Capabilities]
 * - Device Capability: Device info, secure storage
 * - Biometric Capability: Fingerprint/Face recognition
 * - Camera Capability: Photo capture, gallery access
 * - Location Capability: GPS positioning
 * - Push Notification Capability: Push messages
 *
 * [Design Notes]
 * - This module is a pure contract definition layer, containing no concrete implementations
 * - Framework-agnostic: Does not depend on React Native or other UI frameworks
 * - Plugins only need to depend on this module
 * - For React Native bindings, use @brix/runtime-sdk-react-native (Phase 2)
 *
 * [v3.2.0 Notes]
 * - Created mobile contract package based on runtime-sdk-api-web pattern
 * - Reuses shared capabilities (Http, Auth, State, EventBus, Config)
 * - Added mobile-specific capabilities (Device, Biometric, Camera, Location, PushNotification)
 */

// =========================================
// Re-export all type definitions from types/
// =========================================
export * from './types';

// =========================================
// Re-export runtime context from context/
// =========================================
export * from './context';
