/**
 * @file Camera capability type definitions
 * @description Define camera photo capture and gallery access capability contract
 * @module @brix/runtime-sdk-api-mobile/types/camera
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Mobile-specific capability, provides camera photo capture and gallery image selection.
 *
 * [Architecture Notes]
 * - Unified image acquisition entry
 * - Supports permission management
 * - Supports image compression and cropping
 */

// =========================================
// Camera Capability Type Identifier
// =========================================

/**
 * Camera Capability Type Identifier
 */
export const CameraCapabilityType = Symbol.for('CameraCapability');

// =========================================
// Camera Options
// =========================================

/**
 * Camera Photo Capture Options
 */
export interface CameraOptions {
  /** Image quality (0-1) */
  readonly quality?: number;
  /** Whether to allow editing */
  readonly allowsEditing?: boolean;
  /** Image type */
  readonly mediaType?: 'photo' | 'video' | 'mixed';
  /** Use front or back camera */
  readonly cameraType?: 'front' | 'back';
  /** Max width (pixels) */
  readonly maxWidth?: number;
  /** Max height (pixels) */
  readonly maxHeight?: number;
  /** Whether to include Base64 encoding */
  readonly includeBase64?: boolean;
  /** Whether to save to photo album */
  readonly saveToPhotos?: boolean;
}

/**
 * Gallery Selection Options
 */
export interface GalleryOptions {
  /** Image quality (0-1) */
  readonly quality?: number;
  /** Whether to allow editing */
  readonly allowsEditing?: boolean;
  /** Media type */
  readonly mediaType?: 'photo' | 'video' | 'mixed';
  /** Max selection count */
  readonly selectionLimit?: number;
  /** Max width (pixels) */
  readonly maxWidth?: number;
  /** Max height (pixels) */
  readonly maxHeight?: number;
  /** Whether to include Base64 encoding */
  readonly includeBase64?: boolean;
}

// =========================================
// Image Result
// =========================================

/**
 * Image Asset Info
 */
export interface ImageAsset {
  /** Image URI */
  readonly uri: string;
  /** Image width */
  readonly width: number;
  /** Image height */
  readonly height: number;
  /** File size (bytes) */
  readonly fileSize?: number;
  /** MIME type */
  readonly type?: string;
  /** File name */
  readonly fileName?: string;
  /** Base64 encoding (if requested) */
  readonly base64?: string;
}

/**
 * Image Selection Result
 */
export interface ImageResult {
  /** Whether cancelled */
  readonly cancelled: boolean;
  /** Selected image list */
  readonly assets?: ImageAsset[];
  /** Error message */
  readonly error?: string;
}

// =========================================
// Camera Permission
// =========================================

/**
 * Camera Permission Status
 */
export type CameraPermissionStatus =
  | 'granted'       // Granted
  | 'denied'        // Denied
  | 'restricted'    // Restricted (iOS parental controls, etc.)
  | 'undetermined'; // Undetermined (first request)

/**
 * Camera Permission Result
 */
export interface CameraPermissionResult {
  /** Camera permission status */
  readonly camera: CameraPermissionStatus;
  /** Photo library permission status */
  readonly photoLibrary: CameraPermissionStatus;
}

// =========================================
// Camera Capability Contract
// =========================================

/**
 * Camera Capability Contract
 *
 * <p>Provides camera photo capture and gallery access capability for plugins.</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const camera = context.getCapability<CameraCapability>(CameraCapabilityType);
 * 
 * // Request permission
 * const granted = await camera.requestPermission();
 * 
 * if (granted) {
 *   // Take photo
 *   const result = await camera.takePicture({
 *     quality: 0.8,
 *     maxWidth: 1024,
 *   });
 *   
 *   if (!result.cancelled && result.assets) {
 *     const photoUri = result.assets[0].uri;
 *   }
 *   
 *   // Pick from gallery
 *   const galleryResult = await camera.pickFromGallery({
 *     selectionLimit: 5,
 *   });
 * }
 * ```
 */
export interface CameraCapability {
  /**
   * Take photo
   *
   * @param options Photo capture options
   * @returns Image result
   */
  takePicture(options?: CameraOptions): Promise<ImageResult>;

  /**
   * Pick image from gallery
   *
   * @param options Selection options
   * @returns Image result
   */
  pickFromGallery(options?: GalleryOptions): Promise<ImageResult>;

  /**
   * Request camera permission
   *
   * @returns Whether permission granted
   */
  requestPermission(): Promise<boolean>;

  /**
   * Get permission status
   *
   * @returns Permission status
   */
  getPermissionStatus(): Promise<CameraPermissionResult>;

  /**
   * Check if camera is available
   *
   * @returns Whether camera is available
   */
  isAvailable(): Promise<boolean>;
}
