/**
 * @file Type definitions unified export
 * @description Re-export all type definitions from categorized files
 * @module @brix/runtime-sdk-api-mobile/types
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Created based on runtime-sdk-api-web pattern, includes:
 * 
 * [Shared Capability Types] (consistent with Web version)
 * - capability.ts: Capability system types
 * - plugin.ts: Plugin system types
 * - navigation.ts: Navigation system types
 * - state.ts: State management types
 * - event.ts: Event system types
 * - module.ts: Module system types
 * - http.ts: HTTP client capability types
 * - auth.ts: Authentication capability types
 * - config.ts: Configuration capability types
 * - common.ts: Common utility types and API response types
 * 
 * [Mobile-Specific Capability Types]
 * - device.ts: Device info, secure storage capability
 * - biometric.ts: Biometric (fingerprint/face) capability
 * - camera.ts: Camera, gallery access capability
 * - location.ts: GPS location capability
 * - push-notification.ts: Push notification capability
 *
 * [Design Notes]
 * - Each file has single responsibility
 * - Enables on-demand imports
 * - Easy to maintain and extend
 */

// =========================================
// Capability System Types
// =========================================
export * from './capability';

// =========================================
// Plugin System Types
// =========================================
export * from './plugin';

// =========================================
// Navigation System Types
// =========================================
export * from './navigation';

// =========================================
// State Management Types
// =========================================
export * from './state';

// =========================================
// Event System Types
// =========================================
export * from './event';

// =========================================
// Module System Types
// =========================================
export * from './module';

// =========================================
// HTTP Client Capability Types
// =========================================
export * from './http';

// =========================================
// Authentication Capability Types
// =========================================
export * from './auth';

// =========================================
// Configuration Capability Types
// =========================================
export * from './config';

// =========================================
// Common Utility Types
// =========================================
export * from './common';

// =========================================
// Mobile-Specific Capability Types
// =========================================
export * from './device';
export * from './biometric';
export * from './camera';
export * from './location';
export * from './push-notification';
