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
 * @file @brix/infra-adapter-biometric-mobile Package Entry
 * @description Biometric Authentication Adapter for Mobile
 * @module @brix/infra-adapter-biometric-mobile
 * @version 3.1.0
 *
 * [Architecture Positioning]
 * This package provides biometric authentication capability for mobile platforms.
 * Part of the Infrastructure Adapter layer in the Runtime Shell architecture.
 *
 * [v3.1 Changes]
 * - Extracted from infra-adapter-device-mobile as standalone package
 * - Follows Single Responsibility Principle
 *
 * 【生物识别认证适配器包】
 * 提供移动端生物识别认证能力，属于运行壳架构中的基础设施适配器层。
 *
 * @packageDocumentation
 */

// ============================================================================
// Core Exports
// ============================================================================

export {
  // Adapter
  BiometricCapabilityAdapter,
  createBiometricCapability,

  // Types
  type BiometricCapability,
  type BiometricType,
  type BiometricResult,
  type BiometricErrorCode,
  type BiometricAvailability,
  type BiometricAuthOptions,
} from './BiometricCapabilityAdapter';
