/**
 * @file Device capability type definitions
 * @description Define mobile device info and secure storage capability contracts
 * @module @brix/runtime-sdk-api-mobile/types/device
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Mobile-specific capability, provides device info retrieval and secure storage functionality.
 *
 * [Architecture Notes]
 * - Device info: Platform, version, model, brand, etc.
 * - Secure storage: Encrypted storage based on Keychain (iOS) and Keystore (Android)
 */

// =========================================
// Device Capability Type Identifier
// =========================================

/**
 * Device Capability Type Identifier
 */
export const DeviceCapabilityType = Symbol.for('DeviceCapability');

/**
 * Secure Storage Capability Type Identifier
 */
export const SecureStorageCapabilityType = Symbol.for('SecureStorageCapability');

// =========================================
// Device Info Types
// =========================================

/**
 * Device Platform Type
 */
export type DevicePlatform = 'ios' | 'android';

/**
 * Device Info
 */
export interface DeviceInfo {
  /** Device unique identifier */
  readonly deviceId: string;
  /** Device platform */
  readonly platform: DevicePlatform;
  /** System version */
  readonly systemVersion: string;
  /** Device model */
  readonly model: string;
  /** Device brand */
  readonly brand: string;
  /** Whether tablet */
  readonly isTablet: boolean;
  /** App version */
  readonly appVersion: string;
  /** App build number */
  readonly buildNumber: string;
  /** Device name */
  readonly deviceName?: string;
  /** Device locale */
  readonly locale?: string;
  /** Timezone */
  readonly timezone?: string;
}

// =========================================
// Secure Storage Capability
// =========================================

/**
 * Secure Storage Options
 */
export interface SecureStorageOptions {
  /** 
   * Access control
   * - 'whenUnlocked': Accessible only when device is unlocked
   * - 'afterFirstUnlock': Accessible after first unlock
   * - 'always': Always accessible (not recommended)
   */
  readonly accessible?: 'whenUnlocked' | 'afterFirstUnlock' | 'always';
  
  /** Whether biometric authentication is required for access */
  readonly requireBiometric?: boolean;
}

/**
 * Secure Storage Capability Contract
 *
 * <p>Encrypted storage based on Keychain (iOS) and Keystore (Android).</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const secureStorage = device.getSecureStorage();
 * await secureStorage.setItem('accessToken', token);
 * const token = await secureStorage.getItem('accessToken');
 * ```
 */
export interface SecureStorageCapability {
  /**
   * Store data
   *
   * @param key Key name
   * @param value Value
   * @param options Storage options
   */
  setItem(key: string, value: string, options?: SecureStorageOptions): Promise<void>;

  /**
   * Get data
   *
   * @param key Key name
   * @returns Value, returns null if not found
   */
  getItem(key: string): Promise<string | null>;

  /**
   * Remove data
   *
   * @param key Key name
   */
  removeItem(key: string): Promise<void>;

  /**
   * Clear all data
   */
  clear(): Promise<void>;

  /**
   * Get all key names
   *
   * @returns Key name list
   */
  getAllKeys(): Promise<string[]>;
}

// =========================================
// Device Capability Contract
// =========================================

/**
 * Device Capability Contract
 *
 * <p>Provides device info retrieval and secure storage capability for plugins.</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const device = context.getCapability<DeviceCapability>(DeviceCapabilityType);
 * 
 * const deviceId = device.getDeviceId();
 * const platform = device.getPlatform();
 * 
 * if (device.isTablet()) {
 *   // Tablet layout
 * }
 * 
 * const secureStorage = device.getSecureStorage();
 * await secureStorage.setItem('token', accessToken);
 * ```
 */
export interface DeviceCapability {
  /**
   * Get device unique identifier
   *
   * @returns Device ID
   */
  getDeviceId(): string;

  /**
   * Get device platform
   *
   * @returns 'ios' | 'android'
   */
  getPlatform(): DevicePlatform;

  /**
   * Get system version
   *
   * @returns System version number
   */
  getVersion(): string;

  /**
   * Get device model
   *
   * @returns Device model
   */
  getModel(): string;

  /**
   * Get device brand
   *
   * @returns Device brand
   */
  getBrand(): string;

  /**
   * Check if tablet device
   *
   * @returns Whether tablet
   */
  isTablet(): boolean;

  /**
   * Get complete device info
   *
   * @returns Device info object
   */
  getDeviceInfo(): DeviceInfo;

  /**
   * Get secure storage capability
   *
   * @returns Secure storage capability instance
   */
  getSecureStorage(): SecureStorageCapability;
}
