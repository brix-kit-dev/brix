/**
 * @file infra-adapter-iframe-web Module Entry
 * @description Brix UI iframe Adapter - Plugin isolation loading implementation based on iframe
 * @module @brix/infra-adapter-iframe-web
 * @version 3.0.0
 * 
 * 【Module Description】
 * This module is a UI adapter layer component in the v3.0 Runtime Shell architecture,
 * providing iframe-based plugin isolation loading capability.
 * Serves as a fallback option for Module Federation.
 * 
 * 【Applicable Scenarios】
 * - Embedded Mode: Plugin embedded in customer systems
 * - Security Isolation: Loading untrusted third-party plugins
 * - Legacy System Integration: Integrating non-React legacy applications
 * 
 * 【Core Components】
 * - IframePluginLoader: iframe plugin loader
 * - IframeBridge: Cross-window communication bridge
 * 
 * 【Usage Example】
 * ```typescript
 * import { IframePluginLoader, IframeBridge } from '@brix/infra-adapter-iframe-web';
 * 
 * // Create loader
 * const loader = new IframePluginLoader({
 *   allowedOrigins: ['http://localhost:3010'],
 * });
 * 
 * // Register message handler
 * loader.getBridge().on(IframeBridgeMessageType.NAV_REQUEST, (payload) => {
 *   // Handle navigation request
 * });
 * 
 * // Load plugin
 * const plugin = await loader.load({
 *   id: 'booking',
 *   name: 'Booking Management',
 *   version: '1.0.0',
 *   url: 'http://localhost:3010',
 * });
 * ```
 */

// ========== Core Loader ==========
export { IframePluginLoader, type IframePluginLoaderOptions } from './IframePluginLoader';

// ========== Communication Bridge ==========
export { IframeBridge, type IframeBridgeOptions, type MessageHandler } from './IframeBridge';

// ========== Type Definitions ==========
export type {
  IframePluginManifest,
  IframePluginInstance,
  IframePluginStatus,
  IframeBridgeMessage,
  InitPayload,
  NavRequestPayload,
  NavResponsePayload,
  EventPayload,
  StateRequestPayload,
  StateResponsePayload,
} from './types';

export { 
  IframeBridgeMessageType,
  IframeLoadError,
  IframeBridgeError,
} from './types';

// ========== Version Info ==========
export const VERSION = '3.0.0';
