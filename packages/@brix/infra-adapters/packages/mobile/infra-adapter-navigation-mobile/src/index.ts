/**
 * @file infra-adapter-navigation-mobile Module Entry
 * @description Brix UI Mobile Navigation Adapter - React Navigation routing implementation
 * @module @brix/infra-adapter-navigation-mobile
 * @version 3.0.0
 * 
 * Module Description:
 * This module is the Mobile navigation adapter layer in the v3.0 Runtime Shell architecture.
 * It wraps React Navigation and provides a unified navigation capability interface.
 * 
 * Architecture Position:
 * - This module is an internal dependency of the Mobile Host layer
 * - Plugins should NOT use this module directly
 * - Plugins operate navigation through the NavigationCapability contract
 * 
 * v3.0 Boundary Constraints:
 * ❌ Plugins must NOT directly operate Navigation Container
 * ❌ Plugins must NOT access other plugins' navigation state
 * ❌ Plugins must NOT register global Deep Link handlers
 * ✅ Plugins declare navigation intent through NavigationCapability
 * ✅ Navigation permissions are controlled by Host
 * 
 * Usage (Host layer only):
 * ```typescript
 * import { RNNavigationAdapter, createScreenId } from '@brix/infra-adapter-navigation-mobile';
 * 
 * const adapter = new RNNavigationAdapter({
 *   screenRegistry: registry,
 *   initialScreenId: createScreenId('home', 'main'),
 * });
 * 
 * adapter.navigateTo('booking:detail', { bookingId: '123' });
 * ```
 */

export {
  RNNavigationAdapter,
  createScreenId,
  parseScreenId,
  type ScreenId,
  type ScreenMetadata,
  type ScreenOptions,
  type ScreenRegistry,
  type NavigationParams,
  type NavigationEvent,
  type NavigationState,
  type RNNavigationAdapterOptions,
} from './RNNavigationAdapter';

// ========== Version Info ==========
export const VERSION = '3.0.0';
