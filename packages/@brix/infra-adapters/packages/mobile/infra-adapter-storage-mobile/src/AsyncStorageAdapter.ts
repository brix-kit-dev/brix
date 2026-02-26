/**
 * @file AsyncStorage Storage Adapter
 * @description Brix UI Mobile Persistent Storage Implementation - Based on @react-native-async-storage/async-storage
 * @module @brix/infra-adapter-storage-mobile
 * @version 3.0.0
 * 
 * Architecture Overview:
 * This adapter is the Mobile storage layer in v3.0 Runtime Shell architecture.
 * Wraps AsyncStorage to implement namespace isolation for plugin storage.
 * 
 * v3.0 Architecture Positioning:
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
 * │    │        StorageCapability Contract Interface        │     │
 * │    │  - get(key): Promise<T>                         │     │
 * │    │  - set(key, value): Promise<void>               │     │
 * │    │  - remove(key): Promise<void>                   │     │
 * │    └─────────────────────────────────────────────────┘     │
 * │                           │                                │
 * │                           ▼                                │
 * │    ┌─────────────────────────────────────────────────┐     │
 * │    │      AsyncStorageAdapter (This Adapter)          │     │
 * │    │  - Namespace isolation                           │     │
 * │    │  - Data serialization/deserialization            │     │
 * │    │  - Storage quota management                      │     │
 * │    └─────────────────────────────────────────────────┘     │
 * │                           │                                │
 * │                           ▼                                │
 * │    ┌─────────────────────────────────────────────────┐     │
 * │    │       @react-native-async-storage/async-storage  │     │
 * │    └─────────────────────────────────────────────────┘     │
 * └─────────────────────────────────────────────────────────────┘
 * ```
 * 
 * Storage Isolation Strategy:
 * Each plugin's data is stored under an isolated namespace:
 * - Key format: `@brix:{pluginId}:{key}`
 * - Plugins can only access data in their own namespace
 * - Host can manage data across all plugins
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
 * // Set data for plugin
 * await adapter.set('booking', 'userPrefs', { theme: 'dark' });
 * 
 * // Get plugin data
 * const prefs = await adapter.get('booking', 'userPrefs');
 * ```
 */

import AsyncStorage from '@react-native-async-storage/async-storage';

// ========== Type Definitions ==========

/**
 * Storage key prefix constant
 */
const STORAGE_PREFIX = '@brix';

/**
 * Storage item metadata
 */
export interface StorageItemMetadata {
  /** Storage timestamp */
  storedAt: number;
  /** Data size (bytes) */
  size: number;
  /** Expiration time (optional) */
  expiresAt?: number;
}

/**
 * Storage item with metadata
 */
interface StorageItemWithMetadata<T = unknown> {
  /** Storage value */
  value: T;
  /** Metadata */
  metadata: StorageItemMetadata;
}

/**
 * Storage statistics
 */
export interface StorageStats {
  /** Plugin ID */
  pluginId: string;
  /** Used storage (bytes) */
  usedBytes: number;
  /** Number of storage items */
  itemCount: number;
  /** Quota limit (bytes) */
  quotaBytes: number;
  /** Usage percentage */
  usagePercent: number;
}

/**
 * Storage change event
 */
export interface StorageChangeEvent {
  /** Event type */
  type: 'set' | 'remove' | 'clear';
  /** Plugin ID */
  pluginId: string;
  /** Storage key */
  key?: string;
  /** Event timestamp */
  timestamp: number;
}

/**
 * Storage change listener
 */
export type StorageChangeListener = (event: StorageChangeEvent) => void;

/**
 * AsyncStorageAdapter configuration options
 */
export interface AsyncStorageAdapterOptions {
  /** Maximum storage quota per plugin (bytes), default 5MB */
  maxStoragePerPlugin?: number;
  /** Storage change callback */
  onStorageChange?: StorageChangeListener;
  /** Whether to enable storage statistics */
  enableStats?: boolean;
}

// ========== Core Implementation ==========

/**
 * AsyncStorage Storage Adapter
 * 
 * Responsibilities:
 * - Manage namespace isolation for plugin storage
 * - Provide type-safe storage operations
 * - Implement storage quota management
 * - Support storage item expiration mechanism
 * 
 * Internal Implementation:
 * - All data serialized in JSON format
 * - Namespace prefix automatically added
 * - Maintain usage statistics per plugin
 * 
 * @example
 * ```typescript
 * const adapter = new AsyncStorageAdapter({
 *   maxStoragePerPlugin: 5 * 1024 * 1024,
 * });
 * 
 * // Store data
 * await adapter.set('booking', 'selectedDate', new Date().toISOString());
 * 
 * // Read data
 * const date = await adapter.get<string>('booking', 'selectedDate');
 * 
 * // Delete data
 * await adapter.remove('booking', 'selectedDate');
 * ```
 */
export class AsyncStorageAdapter {
  /** Configuration options */
  private readonly options: Required<AsyncStorageAdapterOptions>;
  
  /** Storage usage cache */
  private readonly usageCache: Map<string, number> = new Map();
  
  /** Storage change listeners */
  private readonly listeners: Set<StorageChangeListener> = new Set();

  /** Default configuration */
  private static readonly DEFAULT_OPTIONS: Required<AsyncStorageAdapterOptions> = {
    maxStoragePerPlugin: 5 * 1024 * 1024, // 5MB
    onStorageChange: () => {},
    enableStats: true,
  };

  /**
   * Create AsyncStorageAdapter instance
   * 
   * @param options - Adapter configuration
   */
  constructor(options: AsyncStorageAdapterOptions = {}) {
    this.options = {
      ...AsyncStorageAdapter.DEFAULT_OPTIONS,
      ...options,
    };

    if (this.options.onStorageChange) {
      this.listeners.add(this.options.onStorageChange);
    }
  }

  /**
   * Get storage value
   * 
   * @param pluginId - Plugin ID
   * @param key - Storage key
   * @returns Stored value, returns null if not exists or expired
   * 
   * @example
   * ```typescript
   * const theme = await adapter.get<string>('booking', 'theme');
   * ```
   */
  async get<T = unknown>(pluginId: string, key: string): Promise<T | null> {
    const storageKey = this.buildStorageKey(pluginId, key);
    
    try {
      const raw = await AsyncStorage.getItem(storageKey);
      if (!raw) {
        return null;
      }

      const item: StorageItemWithMetadata<T> = JSON.parse(raw);

      // Check if expired
      if (item.metadata.expiresAt && Date.now() > item.metadata.expiresAt) {
        // Async cleanup of expired data
        this.remove(pluginId, key).catch(() => {});
        return null;
      }

      return item.value;
    } catch {
      return null;
    }
  }

  /**
   * Set storage value
   * 
   * @param pluginId - Plugin ID
   * @param key - Storage key
   * @param value - Storage value
   * @param ttl - Expiration time (milliseconds), optional
   * @throws Throws error when quota exceeded
   * 
   * @example
   * ```typescript
   * // Permanent storage
   * await adapter.set('booking', 'prefs', { theme: 'dark' });
   * 
   * // Expires in 1 hour
   * await adapter.set('booking', 'cache', data, 3600000);
   * ```
   */
  async set<T = unknown>(
    pluginId: string,
    key: string,
    value: T,
    ttl?: number
  ): Promise<void> {
    const storageKey = this.buildStorageKey(pluginId, key);

    const item: StorageItemWithMetadata<T> = {
      value,
      metadata: {
        storedAt: Date.now(),
        size: 0, // Will be calculated below
        expiresAt: ttl ? Date.now() + ttl : undefined,
      },
    };

    const serialized = JSON.stringify(item);
    item.metadata.size = new TextEncoder().encode(serialized).length;

    // Check quota
    const currentUsage = await this.getPluginUsage(pluginId);
    const existingSize = await this.getItemSize(pluginId, key);
    const newUsage = currentUsage - existingSize + item.metadata.size;

    if (newUsage > this.options.maxStoragePerPlugin) {
      throw new Error(
        `[AsyncStorageAdapter] Storage quota exceeded for plugin: ${pluginId}. ` +
        `Current: ${currentUsage}, New item: ${item.metadata.size}, ` +
        `Quota: ${this.options.maxStoragePerPlugin}`
      );
    }

    // Store data
    await AsyncStorage.setItem(storageKey, serialized);

    // Update usage cache
    this.usageCache.set(pluginId, newUsage);

    // Trigger change event
    this.emitChangeEvent({
      type: 'set',
      pluginId,
      key,
      timestamp: Date.now(),
    });
  }

  /**
   * Delete storage value
   * 
   * @param pluginId - Plugin ID
   * @param key - Storage key
   * 
   * @example
   * ```typescript
   * await adapter.remove('booking', 'tempData');
   * ```
   */
  async remove(pluginId: string, key: string): Promise<void> {
    const storageKey = this.buildStorageKey(pluginId, key);
    const existingSize = await this.getItemSize(pluginId, key);

    await AsyncStorage.removeItem(storageKey);

    // Update usage cache
    const currentUsage = this.usageCache.get(pluginId) || 0;
    this.usageCache.set(pluginId, Math.max(0, currentUsage - existingSize));

    // Trigger change event
    this.emitChangeEvent({
      type: 'remove',
      pluginId,
      key,
      timestamp: Date.now(),
    });
  }

  /**
   * Clear all storage for a plugin
   * 
   * @param pluginId - Plugin ID
   * 
   * @example
   * ```typescript
   * await adapter.clearPlugin('booking');
   * ```
   */
  async clearPlugin(pluginId: string): Promise<void> {
    const keys = await this.getPluginKeys(pluginId);
    
    if (keys.length > 0) {
      await AsyncStorage.multiRemove(keys);
    }

    // Clear usage cache
    this.usageCache.delete(pluginId);

    // Trigger change event
    this.emitChangeEvent({
      type: 'clear',
      pluginId,
      timestamp: Date.now(),
    });
  }

  /**
   * Get all keys for a plugin
   * 
   * @param pluginId - Plugin ID
   * @returns Storage key list (without prefix)
   */
  async getAllKeys(pluginId: string): Promise<string[]> {
    const prefix = `${STORAGE_PREFIX}:${pluginId}:`;
    const allKeys = await AsyncStorage.getAllKeys();
    
    return allKeys
      .filter((key) => key.startsWith(prefix))
      .map((key) => key.slice(prefix.length));
  }

  /**
   * Get plugin storage statistics
   * 
   * @param pluginId - Plugin ID
   * @returns Storage statistics
   */
  async getStats(pluginId: string): Promise<StorageStats> {
    const keys = await this.getPluginKeys(pluginId);
    let totalSize = 0;

    if (keys.length > 0) {
      const pairs = await AsyncStorage.multiGet(keys);
      for (const [, value] of pairs) {
        if (value) {
          totalSize += new TextEncoder().encode(value).length;
        }
      }
    }

    // Update cache
    this.usageCache.set(pluginId, totalSize);

    return {
      pluginId,
      usedBytes: totalSize,
      itemCount: keys.length,
      quotaBytes: this.options.maxStoragePerPlugin,
      usagePercent: (totalSize / this.options.maxStoragePerPlugin) * 100,
    };
  }

  /**
   * Batch get multiple values
   * 
   * @param pluginId - Plugin ID
   * @param keys - Storage key list
   * @returns Key-value map
   */
  async multiGet<T = unknown>(
    pluginId: string,
    keys: string[]
  ): Promise<Map<string, T | null>> {
    const storageKeys = keys.map((key) => this.buildStorageKey(pluginId, key));
    const pairs = await AsyncStorage.multiGet(storageKeys);
    const result = new Map<string, T | null>();

    pairs.forEach(([storageKey, raw], index) => {
      const key = keys[index];
      
      // Safety check: ensure key exists
      if (!key) {
        return;
      }
      
      if (!raw) {
        result.set(key, null);
        return;
      }

      try {
        const item: StorageItemWithMetadata<T> = JSON.parse(raw);
        
        // Check expiration
        if (item.metadata.expiresAt && Date.now() > item.metadata.expiresAt) {
          result.set(key, null);
          // Async cleanup (safety check storageKey)
          if (storageKey) {
            AsyncStorage.removeItem(storageKey).catch(() => {});
          }
          return;
        }

        result.set(key, item.value);
      } catch {
        result.set(key, null);
      }
    });

    return result;
  }

  /**
   * Add storage change listener
   * 
   * @param listener - Listener function
   * @returns Unsubscribe function
   */
  addChangeListener(listener: StorageChangeListener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  // ========== Private Methods ==========

  /**
   * Build complete storage key
   */
  private buildStorageKey(pluginId: string, key: string): string {
    return `${STORAGE_PREFIX}:${pluginId}:${key}`;
  }

  /**
   * Get all storage keys for a plugin (with prefix)
   */
  private async getPluginKeys(pluginId: string): Promise<string[]> {
    const prefix = `${STORAGE_PREFIX}:${pluginId}:`;
    const allKeys = await AsyncStorage.getAllKeys();
    return allKeys.filter((key) => key.startsWith(prefix));
  }

  /**
   * Get plugin's used storage amount
   */
  private async getPluginUsage(pluginId: string): Promise<number> {
    // Prefer using cache
    const cached = this.usageCache.get(pluginId);
    if (cached !== undefined) {
      return cached;
    }

    // Calculate actual usage
    const stats = await this.getStats(pluginId);
    return stats.usedBytes;
  }

  /**
   * Get size of a single storage item
   */
  private async getItemSize(pluginId: string, key: string): Promise<number> {
    const storageKey = this.buildStorageKey(pluginId, key);
    
    try {
      const raw = await AsyncStorage.getItem(storageKey);
      if (!raw) return 0;
      return new TextEncoder().encode(raw).length;
    } catch {
      return 0;
    }
  }

  /**
   * Trigger change event
   */
  private emitChangeEvent(event: StorageChangeEvent): void {
    this.listeners.forEach((listener) => {
      try {
        listener(event);
      } catch {
        // Ignore listener errors
      }
    });
  }
}
