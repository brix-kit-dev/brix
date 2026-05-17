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
 * @file Location capability type definitions
 * @description Define GPS location capability contract
 * @module @brix-sdk/runtime-sdk-api-mobile/types/location
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Mobile-specific capability, provides GPS location functionality.
 *
 * [Architecture Notes]
 * - Single location and continuous monitoring
 * - Permission management
 * - Supports background location (requires additional configuration)
 */

import type { Subscription } from './common';

// =========================================
// Location Capability Type Identifier
// =========================================

/**
 * Location Capability Type Identifier
 */
export const LocationCapabilityType = Symbol.for('LocationCapability');

// =========================================
// Geographic Location Types
// =========================================

/**
 * Geographic Coordinates
 */
export interface GeoCoordinates {
  /** Latitude */
  readonly latitude: number;
  /** Longitude */
  readonly longitude: number;
  /** Altitude (meters) */
  readonly altitude?: number;
  /** Accuracy (meters) */
  readonly accuracy?: number;
  /** Altitude accuracy (meters) */
  readonly altitudeAccuracy?: number;
  /** Heading (degrees, 0-360) */
  readonly heading?: number;
  /** Speed (meters/second) */
  readonly speed?: number;
}

/**
 * Geographic Position
 */
export interface GeoPosition {
  /** Coordinate info */
  readonly coords: GeoCoordinates;
  /** Timestamp */
  readonly timestamp: number;
}

/**
 * Location Options
 */
export interface LocationOptions {
  /** 
   * Accuracy requirement
   * - 'high': High accuracy (GPS)
   * - 'balanced': Balanced (Network + GPS)
   * - 'low': Low accuracy (Network only)
   */
  readonly accuracy?: 'high' | 'balanced' | 'low';
  /** Timeout (milliseconds) */
  readonly timeout?: number;
  /** Maximum cache age (milliseconds) */
  readonly maximumAge?: number;
  /** Whether to enable high accuracy (GPS) */
  readonly enableHighAccuracy?: boolean;
  /** Minimum interval for position updates (milliseconds) */
  readonly interval?: number;
  /** Minimum distance (meters), updates below this value are not triggered */
  readonly distanceFilter?: number;
}

/**
 * Location Error
 */
export interface LocationError {
  /** Error code */
  readonly code: LocationErrorCode;
  /** Error message */
  readonly message: string;
}

/**
 * Location Error Code
 */
export type LocationErrorCode =
  | 'PERMISSION_DENIED'     // Permission denied
  | 'POSITION_UNAVAILABLE'  // Position unavailable
  | 'TIMEOUT'               // Timeout
  | 'PLAY_SERVICE_NOT_AVAILABLE' // Google Play services unavailable (Android)
  | 'SETTINGS_NOT_SATISFIED' // Device location settings not satisfied
  | 'UNKNOWN';              // Unknown error

// =========================================
// Location Permission
// =========================================

/**
 * Location Permission Type
 */
export type LocationPermissionType =
  | 'whenInUse'   // Allow when in use
  | 'always';     // Always allow

/**
 * Location Permission Status
 */
export type LocationPermissionStatus =
  | 'granted'       // Granted
  | 'denied'        // Denied
  | 'restricted'    // Restricted
  | 'undetermined'; // Undetermined

// =========================================
// Location Capability Contract
// =========================================

/**
 * Location Capability Contract
 *
 * <p>Provides GPS location capability for plugins.</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const location = context.getCapability<LocationCapability>(LocationCapabilityType);
 * 
 * // Request permission
 * const granted = await location.requestPermission();
 * 
 * if (granted) {
 *   // Get current position
 *   const position = await location.getCurrentPosition();
 *   console.log(`Current position: ${position.coords.latitude}, ${position.coords.longitude}`);
 *   
 *   // Watch position changes
 *   const subscription = location.watchPosition((pos) => {
 *     console.log('Position update:', pos);
 *   });
 *   
 *   // Stop watching
 *   subscription.unsubscribe();
 * }
 * ```
 */
export interface LocationCapability {
  /**
   * Get current position
   *
   * @param options Location options
   * @returns Geographic position
   */
  getCurrentPosition(options?: LocationOptions): Promise<GeoPosition>;

  /**
   * Watch position changes
   *
   * @param callback Position update callback
   * @param options Location options
   * @returns Subscription object, call unsubscribe() to stop watching
   */
  watchPosition(
    callback: (position: GeoPosition) => void,
    options?: LocationOptions
  ): Subscription;

  /**
   * Watch position changes (with error handling)
   *
   * @param onSuccess Position update callback
   * @param onError Error callback
   * @param options Location options
   * @returns Subscription object
   */
  watchPositionWithError(
    onSuccess: (position: GeoPosition) => void,
    onError: (error: LocationError) => void,
    options?: LocationOptions
  ): Subscription;

  /**
   * Request location permission
   *
   * @param type Permission type
   * @returns Whether permission granted
   */
  requestPermission(type?: LocationPermissionType): Promise<boolean>;

  /**
   * Get permission status
   *
   * @returns Permission status
   */
  getPermissionStatus(): Promise<LocationPermissionStatus>;

  /**
   * Check if location service is enabled
   *
   * @returns Whether location service is enabled
   */
  isEnabled(): Promise<boolean>;

  /**
   * Open device location settings
   */
  openSettings(): Promise<void>;
}
