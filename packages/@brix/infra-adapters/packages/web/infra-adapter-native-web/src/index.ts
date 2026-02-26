/**
 * @file infra-adapter-native-web Module Entry
 * @description Brix UI Native Module Adapter - Local plugin loading implementation based on dynamic import
 * @module @brix/infra-adapter-native-web
 * @version 3.0.0
 * 
 * 【Module Description】
 * This module is the simplest UI adapter implementation in the v3.0 Runtime Shell architecture.
 * Loads local modules based on browser native dynamic import.
 * 
 * 【Applicable Scenarios】
 * - Development Mode: Directly import components during local development
 * - Simple Deployment: Monolithic applications with all plugins bundled together
 * - SSR Scenarios: Module loading during server-side rendering
 * - Test Scenarios: Simulating plugin loading in unit tests
 * 
 * 【Usage Example】
 * ```typescript
 * import { NativePluginLoader } from '@brix/infra-adapter-native-web';
 * 
 * const loader = new NativePluginLoader();
 * 
 * const plugin = await loader.load({
 *   id: 'booking',
 *   name: 'Booking Management',
 *   version: '1.0.0',
 *   importFn: () => import('../plugins/booking/App'),
 * });
 * 
 * // Render plugin
 * <plugin.component />
 * ```
 */

export { 
  NativePluginLoader,
  NativePluginLoadError,
  type NativePluginManifest,
  type NativePluginInstance,
  type NativePluginStatus,
  type NativePluginLoaderOptions,
} from './NativePluginLoader';

// ========== Version Info ==========
export const VERSION = '3.0.0';
