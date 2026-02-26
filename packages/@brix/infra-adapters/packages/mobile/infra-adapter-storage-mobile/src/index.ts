/**
 * @file infra-adapter-storage-mobile Module Entry
 * @description Brix UI Mobile Storage Adapter - AsyncStorage persistence implementation
 * @module @brix/infra-adapter-storage-mobile
 * @version 3.0.0
 * 
 * Module Overview:
 * This module is the Mobile storage adapter layer in v3.0 Runtime Shell architecture.
 * Wraps AsyncStorage to implement namespace isolation for plugin storage.
 * 
 * Architecture Positioning:
 * - This module is an internal dependency of Mobile Host layer
 * - Plugins should not use this module directly
 * - Plugins operate storage through StorageCapability contract
 * 
 * v3.0 Red Line Constraints:
 * ❌ Plugins MUST NOT use AsyncStorage directly
 * ❌ Plugins MUST NOT access other plugins' storage
 * ❌ Plugins MUST NOT bypass quota limits
 * ✅ Plugins operate isolated storage through StorageCapability
 * ✅ Storage quota is managed by Host uniformly
 * 
 * Usage Example: (Host layer only)
 * ```typescript
 * import { AsyncStorageAdapter } from '@brix/infra-adapter-storage-mobile';
 * 
 * const adapter = new AsyncStorageAdapter({
 *   maxStoragePerPlugin: 5 * 1024 * 1024, // 5MB
 * });
 * 
 * await adapter.set('booking', 'userPrefs', { theme: 'dark' });
 * ```
 */

export {
  AsyncStorageAdapter,
  type StorageItemMetadata,
  type StorageStats,
  type StorageChangeEvent,
  type StorageChangeListener,
  type AsyncStorageAdapterOptions,
} from './AsyncStorageAdapter';

// ========== Version Info ==========
export const VERSION = '3.0.0';
