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
 * @file infra-adapter-device-mobile Module Entry
 * @description Brix UI Mobile Device Capability Adapter - Camera, Location and other native capability wrapper
 * @module @brix/infra-adapter-device-mobile
 * @version 3.0.0
 * 
 * Module Description:
 * This module is the Mobile device capability adapter layer in the v3.0 Runtime Shell architecture.
 * It wraps native device capabilities and provides a unified device capability access interface.
 * 
 * Architecture Position:
 * - This module is an internal dependency of the Mobile Host layer
 * - Plugins should NOT use this module directly
 * - Plugins operate device capabilities through the DeviceCapability contract
 * 
 * v3.0 Boundary Constraints:
 * ❌ Plugins must NOT directly use Native Modules
 * ❌ Plugins must NOT bypass permission checks
 * ❌ Plugins must NOT continuously track location in background (unless declared)
 * ✅ Plugins declare required capabilities through DeviceCapability
 * ✅ Permission requests are managed by Host
 * 
 * Usage (Host layer only):
 * ```typescript
 * import { DeviceCapabilityAdapter } from '@brix/infra-adapter-device-mobile';
 * 
 * const adapter = new DeviceCapabilityAdapter();
 * 
 * const photo = await adapter.takePhoto({ quality: 0.8 });
 * ```
 */

export {
  DeviceCapabilityAdapter,
  type PermissionType,
  type PermissionStatus,
  type PermissionResult,
  type GeoCoordinates,
  type GeoPosition,
  type PositionOptions,
  type CameraOptions,
  type PhotoResult,
  type DeviceInfo,
  type BiometricType,
  type BiometricResult,
  type DeviceCapabilityAdapterOptions,
} from './DeviceCapabilityAdapter';

// ========== Version Info ==========
export const VERSION = '3.0.0';
