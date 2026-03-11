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
 * @file Biometric capability type definitions
 * @description Define biometric (fingerprint/face) authentication capability contract
 * @module @brix/runtime-sdk-api-mobile/types/biometric
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Mobile-specific capability, provides fingerprint and face recognition authentication.
 *
 * [Architecture Notes]
 * - iOS: Touch ID / Face ID
 * - Android: Fingerprint / Face recognition
 */

// =========================================
// Biometric Capability Type Identifier
// =========================================

/**
 * Biometric Capability Type Identifier
 */
export const BiometricCapabilityType = Symbol.for('BiometricCapability');

// =========================================
// Biometric Types
// =========================================

/**
 * Biometric Type
 */
export type BiometricType = 'fingerprint' | 'face' | 'iris' | 'none';

/**
 * Biometric Authentication Result
 */
export interface BiometricAuthResult {
  /** Whether authentication succeeded */
  readonly success: boolean;
  /** Error info (on authentication failure) */
  readonly error?: BiometricError;
}

/**
 * Biometric Error
 */
export interface BiometricError {
  /** Error code */
  readonly code: BiometricErrorCode;
  /** Error message */
  readonly message: string;
}

/**
 * Biometric Error Code
 */
export type BiometricErrorCode =
  | 'NOT_AVAILABLE'       // Device does not support biometrics
  | 'NOT_ENROLLED'        // User has not enrolled biometrics
  | 'AUTHENTICATION_FAILED' // Authentication failed
  | 'USER_CANCEL'         // User cancelled
  | 'USER_FALLBACK'       // User chose to use password
  | 'SYSTEM_CANCEL'       // System cancelled
  | 'LOCKOUT'             // Locked out after multiple failures
  | 'LOCKOUT_PERMANENT'   // Permanently locked out
  | 'UNKNOWN';            // Unknown error

/**
 * Biometric Authentication Options
 */
export interface BiometricAuthOptions {
  /** Authentication prompt message */
  readonly reason: string;
  /** Cancel button text */
  readonly cancelTitle?: string;
  /** Use password button text (iOS only) */
  readonly fallbackTitle?: string;
  /** Whether to allow device credential as fallback (Android only) */
  readonly allowDeviceCredential?: boolean;
  /** Authentication title (Android only) */
  readonly title?: string;
  /** Authentication subtitle (Android only) */
  readonly subtitle?: string;
}

// =========================================
// Biometric Capability Contract
// =========================================

/**
 * Biometric Capability Contract
 *
 * <p>Provides biometric authentication capability for plugins.</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const biometric = context.getCapability<BiometricCapability>(BiometricCapabilityType);
 * 
 * // Check availability
 * const available = await biometric.isAvailable();
 * 
 * if (available) {
 *   // Get biometric type
 *   const type = await biometric.getType();
 *   console.log(`Using ${type === 'face' ? 'Face ID' : 'Fingerprint'}`);
 *   
 *   // Perform authentication
 *   const result = await biometric.authenticate('Please verify your identity to continue');
 *   if (result) {
 *     // Authentication succeeded
 *   }
 * }
 * ```
 */
export interface BiometricCapability {
  /**
   * Check if biometrics is available
   *
   * <p>Checks if device supports biometrics and user has enrolled biometrics.</p>
   *
   * @returns Whether available
   */
  isAvailable(): Promise<boolean>;

  /**
   * Perform biometric authentication
   *
   * @param reason Authentication reason prompt
   * @returns Whether authentication succeeded
   */
  authenticate(reason: string): Promise<boolean>;

  /**
   * Perform biometric authentication (full options)
   *
   * @param options Authentication options
   * @returns Authentication result
   */
  authenticateWithOptions(options: BiometricAuthOptions): Promise<BiometricAuthResult>;

  /**
   * Get supported biometric type
   *
   * @returns Biometric type
   */
  getType(): Promise<BiometricType>;

  /**
   * Check if biometrics is enrolled
   *
   * @returns Whether enrolled
   */
  isEnrolled(): Promise<boolean>;
}
