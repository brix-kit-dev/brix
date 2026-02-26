/**
 * @file Device Capability Adapter
 * @description Brix UI Mobile native device capability wrapper - Camera, Location, Permissions, etc.
 * @module @brix/infra-adapter-device-mobile
 * @version 3.0.0
 * 
 * Design Notes:
 * This adapter is the Mobile device capability layer of the v3.0 Runtime Shell architecture.
 * It wraps native device capabilities and provides a unified device capability access interface.
 * 
 * v3.0 Architecture Position:
 * ```
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    Mobile Plugin Layer                      │
 * │    ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
 * │    │  Booking    │  │  Products   │  │  Partners   │       │
 * │    │  Plugin     │  │  Plugin     │  │  Plugin     │       │
 * │    └──────┬──────┘  └──────┬──────┘  └──────┬──────┘       │
 * │           │                │                │              │
 * │           ▼                ▼                ▼              │
 * │    ┌─────────────────────────────────────────────────┐     │
 * │    │        DeviceCapability Contract Interface       │     │
 * │    │  - camera.takePhoto()                           │     │
 * │    │  - location.getCurrentPosition()                │     │
 * │    │  - permissions.request('camera')                │     │
 * │    └─────────────────────────────────────────────────┘     │
 * │                           │                                │
 * │                           ▼                                │
 * │    ┌─────────────────────────────────────────────────┐     │
 * │    │      DeviceCapabilityAdapter (this adapter)      │     │
 * │    │  - Permission management and checking            │     │
 * │    │  - Device capability wrapping                    │     │
 * │    │  - Capability availability detection             │     │
 * │    └─────────────────────────────────────────────────┘     │
 * │                           │                                │
 * │                           ▼                                │
 * │    ┌─────────────────────────────────────────────────┐     │
 * │    │           React Native Native Modules            │     │
 * │    │   (Camera, Geolocation, PermissionsAndroid...)   │     │
 * │    └─────────────────────────────────────────────────┘     │
 * └─────────────────────────────────────────────────────────────┘
 * ```
 * 
 * Device Capability Categories:
 * - Camera: Photo capture, QR code scanning
 * - Location: Position retrieval, geofencing
 * - Permissions: Permission requests and checks
 * - DeviceInfo: Device information retrieval
 * - Biometrics: Biometric authentication
 * 
 * v3.0 Boundary Constraints:
 * ❌ Plugins must NOT directly use Native Modules
 * ❌ Plugins must NOT bypass permission checks
 * ❌ Plugins must NOT continuously track location in background (unless declared)
 * ✅ Plugins declare required capabilities through DeviceCapability
 * ✅ Permission requests are managed by Host
 * 
 * Usage Example (Host layer only):
 * ```typescript
 * import { DeviceCapabilityAdapter } from '@brix/infra-adapter-device-mobile';
 * 
 * const adapter = new DeviceCapabilityAdapter();
 * 
 * // Check camera permission
 * const hasPermission = await adapter.checkPermission('camera');
 * 
 * // Get current position
 * const position = await adapter.getCurrentPosition();
 * ```
 */

// ========== Type Definitions ==========

/**
 * Permission type
 */
export type PermissionType =
  | 'camera'
  | 'photo-library'
  | 'location'
  | 'location-always'
  | 'microphone'
  | 'contacts'
  | 'calendar'
  | 'notifications'
  | 'biometrics';

/**
 * Permission status
 */
export type PermissionStatus =
  | 'granted'      // Authorized
  | 'denied'       // Denied
  | 'blocked'      // Permanently denied (need to enable in settings)
  | 'unavailable'  // Device not supported
  | 'limited'      // Limited (iOS 14+ photo library)
  | 'undetermined'; // Not determined (first request)

/**
 * Permission check result
 */
export interface PermissionResult {
  /** Permission type */
  permission: PermissionType;
  /** Permission status */
  status: PermissionStatus;
  /** Whether can request */
  canRequest: boolean;
}

/**
 * Geographic coordinates
 */
export interface GeoCoordinates {
  /** Latitude */
  latitude: number;
  /** Longitude */
  longitude: number;
  /** Altitude (meters) */
  altitude: number | null;
  /** Accuracy (meters) */
  accuracy: number;
  /** Altitude accuracy (meters) */
  altitudeAccuracy: number | null;
  /** Heading (degrees) */
  heading: number | null;
  /** Speed (meters/second) */
  speed: number | null;
}

/**
 * Geographic position result
 */
export interface GeoPosition {
  /** Coordinate information */
  coords: GeoCoordinates;
  /** Timestamp */
  timestamp: number;
}

/**
 * Position options
 */
export interface PositionOptions {
  /** Enable high accuracy */
  enableHighAccuracy?: boolean;
  /** Timeout (milliseconds) */
  timeout?: number;
  /** Cache validity period (milliseconds) */
  maximumAge?: number;
}

/**
 * Camera options
 */
export interface CameraOptions {
  /** Photo quality (0-1) */
  quality?: number;
  /** Use front/back camera */
  cameraType?: 'front' | 'back';
  /** Maximum width */
  maxWidth?: number;
  /** Maximum height */
  maxHeight?: number;
  /** Include Base64 */
  includeBase64?: boolean;
}

/**
 * Photo result
 */
export interface PhotoResult {
  /** Image URI */
  uri: string;
  /** Base64 encoding (if requested) */
  base64?: string;
  /** Image width */
  width: number;
  /** Image height */
  height: number;
  /** File size (bytes) */
  fileSize?: number;
  /** MIME type */
  type?: string;
}

/**
 * Device information
 */
export interface DeviceInfo {
  /** Device brand */
  brand: string;
  /** Device model */
  model: string;
  /** Device ID */
  deviceId: string;
  /** System name */
  systemName: string;
  /** System version */
  systemVersion: string;
  /** App version */
  appVersion: string;
  /** App build number */
  buildNumber: string;
  /** Whether emulator */
  isEmulator: boolean;
  /** Whether tablet */
  isTablet: boolean;
}

/**
 * Biometric type
 */
export type BiometricType = 'fingerprint' | 'face' | 'iris' | 'none';

/**
 * Biometric result
 */
export interface BiometricResult {
  /** Whether successful */
  success: boolean;
  /** Error message */
  error?: string;
  /** Error type */
  errorType?: 'user_cancel' | 'fallback' | 'lockout' | 'not_enrolled' | 'not_available';
}

/**
 * DeviceCapabilityAdapter configuration options
 */
export interface DeviceCapabilityAdapterOptions {
  /** Custom prompt before permission request */
  onPermissionRequest?: (permission: PermissionType) => Promise<boolean>;
  /** Handler when permission is denied */
  onPermissionDenied?: (permission: PermissionType, status: PermissionStatus) => void;
}

// ========== Core Implementation ==========

/**
 * Device Capability Adapter
 * 
 * Responsibilities:
 * - Wrap native device capabilities
 * - Manage permission request flow
 * - Provide unified device capability interface
 * 
 * Internal Implementation:
 * - Permission check before capability call
 * - All async operations have timeout control
 * - Errors are uniformly wrapped and reported
 * 
 * @example
 * ```typescript
 * const adapter = new DeviceCapabilityAdapter({
 *   onPermissionRequest: async (permission) => {
 *     return await showPermissionDialog(permission);
 *   },
 * });
 * 
 * // Take photo
 * const photo = await adapter.takePhoto({
 *   quality: 0.8,
 *   cameraType: 'back',
 * });
 * 
 * // Get position
 * const position = await adapter.getCurrentPosition({
 *   enableHighAccuracy: true,
 * });
 * ```
 */
export class DeviceCapabilityAdapter {
  /** Configuration options */
  private readonly options: DeviceCapabilityAdapterOptions;

  /**
   * Create DeviceCapabilityAdapter instance
   * 
   * @param options - Adapter configuration
   */
  constructor(options: DeviceCapabilityAdapterOptions = {}) {
    this.options = options;
  }

  // ========== Permission Management ==========

  /**
   * Check permission status
   * 
   * @param permission - Permission type
   * @returns Permission check result
   * 
   * @example
   * ```typescript
   * const result = await adapter.checkPermission('camera');
   * if (result.status === 'granted') {
   *   // Can use camera
   * }
   * ```
   */
  async checkPermission(permission: PermissionType): Promise<PermissionResult> {
    // In actual implementation, this will call react-native-permissions or similar library
    // Below is the interface definition, actual implementation requires Native Module support
    
    throw new Error(
      `[DeviceCapabilityAdapter] Permission check requires Native integration. ` +
      `Permission: ${permission}`
    );
  }

  /**
   * Request permission
   * 
   * @param permission - Permission type
   * @returns Permission request result
   * 
   * @example
   * ```typescript
   * const result = await adapter.requestPermission('camera');
   * ```
   */
  async requestPermission(permission: PermissionType): Promise<PermissionResult> {
    // Call custom prompt first
    if (this.options.onPermissionRequest) {
      const shouldProceed = await this.options.onPermissionRequest(permission);
      if (!shouldProceed) {
        return {
          permission,
          status: 'denied',
          canRequest: true,
        };
      }
    }

    // Actually request permission
    throw new Error(
      `[DeviceCapabilityAdapter] Permission request requires Native integration. ` +
      `Permission: ${permission}`
    );
  }

  /**
   * Batch check permissions
   * 
   * @param permissions - Permission type list
   * @returns Permission check result map
   */
  async checkMultiplePermissions(
    permissions: PermissionType[]
  ): Promise<Map<PermissionType, PermissionResult>> {
    const results = new Map<PermissionType, PermissionResult>();
    
    await Promise.all(
      permissions.map(async (permission) => {
        const result = await this.checkPermission(permission);
        results.set(permission, result);
      })
    );

    return results;
  }

  // ========== Camera Capability ==========

  /**
   * Take photo
   * 
   * @param options - Camera options
   * @returns Photo result
   * 
   * @example
   * ```typescript
   * const photo = await adapter.takePhoto({
   *   quality: 0.8,
   *   maxWidth: 1024,
   *   maxHeight: 1024,
   * });
   * console.log('Photo URI:', photo.uri);
   * ```
   */
  async takePhoto(_options: CameraOptions = {}): Promise<PhotoResult> {
    // Check permission
    const permissionResult = await this.checkPermission('camera');
    if (permissionResult.status !== 'granted') {
      if (permissionResult.canRequest) {
        const requestResult = await this.requestPermission('camera');
        if (requestResult.status !== 'granted') {
          throw new Error('[DeviceCapabilityAdapter] Camera permission denied');
        }
      } else {
        throw new Error('[DeviceCapabilityAdapter] Camera permission blocked');
      }
    }

    // Actually take photo
    throw new Error(
      `[DeviceCapabilityAdapter] Camera functionality requires Native integration.`
    );
  }

  /**
   * Pick image from gallery
   * 
   * @param options - Options
   * @returns Selected image
   */
  async pickFromGallery(_options: CameraOptions = {}): Promise<PhotoResult> {
    // Check permission
    const permissionResult = await this.checkPermission('photo-library');
    if (permissionResult.status !== 'granted' && permissionResult.status !== 'limited') {
      if (permissionResult.canRequest) {
        await this.requestPermission('photo-library');
      }
    }

    throw new Error(
      `[DeviceCapabilityAdapter] Gallery picker requires Native integration.`
    );
  }

  // ========== Location Capability ==========

  /**
   * Get current position
   * 
   * @param options - Position options
   * @returns Geographic position
   * 
   * @example
   * ```typescript
   * const position = await adapter.getCurrentPosition({
   *   enableHighAccuracy: true,
   *   timeout: 15000,
   * });
   * console.log('Latitude:', position.coords.latitude);
   * ```
   */
  async getCurrentPosition(_options: PositionOptions = {}): Promise<GeoPosition> {
    // Check permission
    const permissionResult = await this.checkPermission('location');
    if (permissionResult.status !== 'granted') {
      if (permissionResult.canRequest) {
        const requestResult = await this.requestPermission('location');
        if (requestResult.status !== 'granted') {
          throw new Error('[DeviceCapabilityAdapter] Location permission denied');
        }
      } else {
        throw new Error('[DeviceCapabilityAdapter] Location permission blocked');
      }
    }

    throw new Error(
      `[DeviceCapabilityAdapter] Geolocation requires Native integration.`
    );
  }

  /**
   * Watch position changes
   * 
   * @param callback - Position change callback
   * @param options - Watch options
   * @returns Unsubscribe function
   */
  watchPosition(
    _callback: (position: GeoPosition) => void,
    _options: PositionOptions = {}
  ): () => void {
    throw new Error(
      `[DeviceCapabilityAdapter] Position watching requires Native integration.`
    );
  }

  // ========== Device Information ==========

  /**
   * Get device information
   * 
   * @returns Device information
   * 
   * @example
   * ```typescript
   * const info = await adapter.getDeviceInfo();
   * console.log(`${info.brand} ${info.model} - ${info.systemName} ${info.systemVersion}`);
   * ```
   */
  async getDeviceInfo(): Promise<DeviceInfo> {
    throw new Error(
      `[DeviceCapabilityAdapter] Device info requires Native integration.`
    );
  }

  /**
   * Get supported biometric type
   * 
   * @returns Biometric type
   */
  async getBiometricType(): Promise<BiometricType> {
    throw new Error(
      `[DeviceCapabilityAdapter] Biometric detection requires Native integration.`
    );
  }

  // ========== Biometrics ==========

  /**
   * Biometric authentication
   * 
   * @param promptMessage - Prompt message
   * @returns Authentication result
   * 
   * @example
   * ```typescript
   * const result = await adapter.authenticateWithBiometrics('Please verify identity');
   * if (result.success) {
   *   // Authentication successful
   * }
   * ```
   */
  async authenticateWithBiometrics(_promptMessage: string): Promise<BiometricResult> {
    throw new Error(
      `[DeviceCapabilityAdapter] Biometric authentication requires Native integration.`
    );
  }

  // ========== Capability Detection ==========

  /**
   * Check if device supports specified capability
   * 
   * @param capability - Capability name
   * @returns Whether supported
   */
  async isCapabilitySupported(
    capability: 'camera' | 'location' | 'biometrics' | 'nfc'
  ): Promise<boolean> {
    // 在实际实现中，会检查设备硬件支持
    throw new Error(
      `[DeviceCapabilityAdapter] Capability check requires Native integration. ` +
      `Capability: ${capability}`
    );
  }
}
