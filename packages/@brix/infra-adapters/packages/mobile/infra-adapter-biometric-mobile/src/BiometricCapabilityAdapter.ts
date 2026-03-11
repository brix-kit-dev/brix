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
 * @file Biometric Capability Adapter
 * @description Standalone biometric authentication adapter for Brix Runtime SDK Mobile
 * @module @brix/infra-adapter-biometric-mobile
 * @version 3.1.0
 *
 * [Architecture Positioning]
 * This adapter is extracted from DeviceCapabilityAdapter to provide
 * a standalone biometric authentication capability, following the
 * Single Responsibility Principle.
 *
 * [v3.1 Changes]
 * - Extracted from infra-adapter-device-mobile as independent package
 * - Added BiometricCapability interface implementation
 * - Added platform-specific biometric type detection
 *
 * [Design Principles]
 * - Platform-agnostic interface
 * - Native module abstraction
 * - Secure credential handling
 *
 * 【生物识别能力适配器】
 * 从 DeviceCapabilityAdapter 提取的独立生物识别认证适配器，
 * 提供指纹、面部识别等生物特征认证能力。
 *
 * @author Brix Platform Authors
 * @since 3.1.0
 */

// ============================================================================
// Type Definitions
// ============================================================================

/**
 * Biometric authentication type
 *
 * Represents the type of biometric authentication available on the device.
 */
export type BiometricType =
  | 'fingerprint'  // Fingerprint (Touch ID on iOS, Fingerprint on Android)
  | 'face'         // Face recognition (Face ID on iOS, Face Unlock on Android)
  | 'iris'         // Iris recognition (Samsung devices)
  | 'none';        // No biometric available

/**
 * Biometric authentication result
 *
 * Contains the result of a biometric authentication attempt.
 */
export interface BiometricResult {
  /**
   * Whether authentication was successful
   */
  success: boolean;

  /**
   * Error message if authentication failed
   */
  error?: string;

  /**
   * Error code for programmatic handling
   */
  errorCode?: BiometricErrorCode;
}

/**
 * Biometric error codes
 *
 * Standardized error codes for biometric authentication failures.
 */
export type BiometricErrorCode =
  | 'user_cancel'           // User cancelled authentication
  | 'user_fallback'         // User chose fallback (password/PIN)
  | 'system_cancel'         // System cancelled (app backgrounded)
  | 'not_available'         // Biometric not available
  | 'not_enrolled'          // No biometrics enrolled
  | 'lockout'               // Too many failed attempts
  | 'lockout_permanent'     // Permanent lockout
  | 'no_hardware'           // No biometric hardware
  | 'passcode_not_set'      // Device passcode not set
  | 'unknown';              // Unknown error

/**
 * Biometric availability info
 *
 * Information about biometric availability and configuration.
 */
export interface BiometricAvailability {
  /**
   * Whether biometric authentication is available
   */
  available: boolean;

  /**
   * Type of biometric available
   */
  biometricType: BiometricType;

  /**
   * Whether biometrics are enrolled
   */
  enrolled: boolean;

  /**
   * Reason if not available
   */
  reason?: string;
}

/**
 * Biometric authentication options
 *
 * Configuration options for biometric authentication.
 */
export interface BiometricAuthOptions {
  /**
   * Prompt message to display
   */
  promptMessage: string;

  /**
   * Title for the biometric prompt (Android only)
   */
  title?: string;

  /**
   * Subtitle for the biometric prompt (Android only)
   */
  subtitle?: string;

  /**
   * Description for the biometric prompt (Android only)
   */
  description?: string;

  /**
   * Whether to allow device credentials as fallback
   * @default true
   */
  allowDeviceCredential?: boolean;

  /**
   * Custom cancel button text
   */
  cancelButtonText?: string;

  /**
   * Whether to use strong biometrics only (Android)
   * @default false
   */
  strongBiometricsOnly?: boolean;
}

/**
 * Biometric Capability Interface
 *
 * Defines the contract for biometric authentication capability.
 *
 * 【生物识别能力接口】
 * 定义生物识别认证能力的契约接口。
 */
export interface BiometricCapability {
  /**
   * Check biometric availability
   *
   * @returns Promise resolving to availability info
   */
  checkAvailability(): Promise<BiometricAvailability>;

  /**
   * Get supported biometric type
   *
   * @returns Promise resolving to biometric type
   */
  getBiometricType(): Promise<BiometricType>;

  /**
   * Authenticate using biometrics
   *
   * @param options Authentication options
   * @returns Promise resolving to authentication result
   */
  authenticate(options: BiometricAuthOptions): Promise<BiometricResult>;

  /**
   * Check if biometrics are enrolled
   *
   * @returns Promise resolving to enrollment status
   */
  isEnrolled(): Promise<boolean>;
}

// ============================================================================
// Adapter Implementation
// ============================================================================

/**
 * Biometric Capability Adapter
 *
 * Implements BiometricCapability interface using React Native native modules.
 * This adapter abstracts platform-specific biometric implementations and
 * provides a unified API for biometric authentication.
 *
 * @example
 * ```typescript
 * import { BiometricCapabilityAdapter } from '@brix/infra-adapter-biometric-mobile';
 *
 * const biometric = new BiometricCapabilityAdapter();
 *
 * // Check availability
 * const availability = await biometric.checkAvailability();
 * if (availability.available && availability.enrolled) {
 *   // Authenticate
 *   const result = await biometric.authenticate({
 *     promptMessage: 'Verify your identity',
 *   });
 *
 *   if (result.success) {
 *     console.log('Authentication successful');
 *   }
 * }
 * ```
 *
 * 【生物识别能力适配器实现】
 * 使用 React Native 原生模块实现 BiometricCapability 接口。
 * 抽象平台特定的生物识别实现，提供统一的认证 API。
 *
 * @author Brix Platform Authors
 * @since 3.1.0
 */
export class BiometricCapabilityAdapter implements BiometricCapability {
  /**
   * Native module reference (to be injected by Host layer)
   * 原生模块引用（由 Host 层注入）
   * 
   * Note: Prefixed with underscore to indicate intentionally unused in placeholder.
   * Will be used when native module integration is implemented.
   */
  // @ts-expect-error Reserved for native module integration
  private _nativeModule: unknown = null;

  /**
   * Creates a new BiometricCapabilityAdapter instance
   *
   * @param nativeModule Optional native module injection for testing
   */
  constructor(nativeModule?: unknown) {
    this._nativeModule = nativeModule ?? null;
  }

  /**
   * Check biometric availability
   *
   * Detects whether biometric authentication is available on this device,
   * what type of biometric is supported, and whether biometrics are enrolled.
   *
   * @returns Promise resolving to availability info
   */
  async checkAvailability(): Promise<BiometricAvailability> {
    // Placeholder implementation - requires native module integration
    // 占位实现 - 需要原生模块集成
    console.warn(
      '[BiometricCapabilityAdapter] Biometric availability check requires Native integration. ' +
      'This placeholder returns a mock response.'
    );

    return {
      available: false,
      biometricType: 'none',
      enrolled: false,
      reason: 'Native module not integrated',
    };
  }

  /**
   * Get supported biometric type
   *
   * Returns the type of biometric authentication available on this device.
   *
   * @returns Promise resolving to biometric type
   */
  async getBiometricType(): Promise<BiometricType> {
    const availability = await this.checkAvailability();
    return availability.biometricType;
  }

  /**
   * Authenticate using biometrics
   *
   * Prompts the user for biometric authentication (fingerprint, face, etc.)
   * and returns the authentication result.
   *
   * @param options Authentication options including prompt message
   * @returns Promise resolving to authentication result
   */
  async authenticate(options: BiometricAuthOptions): Promise<BiometricResult> {
    // Validate options
    if (!options.promptMessage) {
      throw new Error('promptMessage is required for biometric authentication');
    }

    // Placeholder implementation - requires native module integration
    // 占位实现 - 需要原生模块集成
    console.warn(
      `[BiometricCapabilityAdapter] Biometric authentication requires Native integration. ` +
      `Prompt: "${options.promptMessage}"`
    );

    return {
      success: false,
      error: 'Native module not integrated',
      errorCode: 'not_available',
    };
  }

  /**
   * Check if biometrics are enrolled
   *
   * Returns whether the user has enrolled biometrics on this device.
   *
   * @returns Promise resolving to enrollment status
   */
  async isEnrolled(): Promise<boolean> {
    const availability = await this.checkAvailability();
    return availability.enrolled;
  }
}

// ============================================================================
// Factory Functions
// ============================================================================

/**
 * Create a new BiometricCapabilityAdapter instance
 *
 * Factory function for creating adapter instances.
 *
 * @param nativeModule Optional native module for testing
 * @returns New BiometricCapabilityAdapter instance
 */
export function createBiometricCapability(nativeModule?: unknown): BiometricCapability {
  return new BiometricCapabilityAdapter(nativeModule);
}
