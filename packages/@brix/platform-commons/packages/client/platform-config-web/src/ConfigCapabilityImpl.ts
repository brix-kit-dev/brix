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
 * @file Configuration Capability Implementation
 * @description Implements ConfigCapability Interface - Provides runtime configuration management
 * @module @brix-sdk/platform-config-web/ConfigCapabilityImpl
 * @version 3.1.0
 *
 * Architecture Overview:
 * ConfigCapabilityImpl is the implementation of ConfigCapability interface,
 * providing configuration loading, caching, and hot-reload capabilities.
 *
 * Core Responsibilities:
 * 1. Load configuration from backend via HttpCapability
 * 2. Support local configuration caching
 * 3. Support configuration hot-reload with polling or push
 * 4. Provide type-safe configuration access
 *
 * Architectural Constraints:
 * - Do not directly call fetch/axios, use HttpCapability
 * - Do not store sensitive configuration in localStorage
 * - All configuration changes must be logged for auditing
 *
 * Key Architecture Points:
 * - Configuration fetches config from backend via HttpCapability, never calls fetch directly
 * - Supports hot-reload via polling or push mechanism
 * - Configuration is cached in memory with TTL expiration
 */

import type {
  ConfigStoreCapability,
  ConfigCapabilityImplOptions,
  ConfigChangeEvent,
  ConfigChangeHandler,
} from '@brix-sdk/runtime-sdk-api-web';
import { ConfigStore, type ConfigStoreOptions } from './ConfigStore';
import { ConfigHttpClient, type ConfigHttpClientOptions } from './ConfigHttpClient';

// Re-export contract-layer types for backward compatibility
export type { ConfigCapabilityImplOptions, ConfigChangeEvent, ConfigChangeHandler };

/**
 * Configuration Capability Implementation
 *
 * Implements ConfigCapability interface, providing configuration management
 * with support for remote loading, caching, and hot-reload.
 *
 * Core Implementation Notes:
 * 1. Loads configuration from backend API via HttpCapability
 * 2. Configuration is cached in ConfigStore with TTL expiration
 * 3. Supports configuration change listeners for hot-reload
 * 4. Supports dot-separated nested key access, e.g. 'api.baseUrl'
 *
 * Usage Example:
 * ```typescript
 * // Create configuration capability
 * const configCapability = new ConfigCapabilityImpl({
 *   httpCapability,
 *   configEndpoint: '/api/v1/config',
 *   refreshInterval: 60000, // Refresh every minute
 * });
 *
 * // Initialize (load initial configuration)
 * await configCapability.initialize();
 *
 * // Get configuration value
 * const apiBase = configCapability.get<string>('api.baseUrl', '/api/v1');
 * const timeout = configCapability.get<number>('http.timeout', 30000);
 *
 * // Listen for configuration changes
 * configCapability.onConfigChange('api.baseUrl', (event) => {
 *   console.log('API base URL changed:', event.newValue);
 * });
 * ```
 */
export class ConfigCapabilityImpl implements ConfigStoreCapability {
  /**
   * Configuration store
   */
  private readonly store: ConfigStore;

  /**
   * Configuration HTTP client
   */
  private readonly httpClient: ConfigHttpClient;

  /**
   * Auto-refresh timer
   */
  private refreshTimer: ReturnType<typeof setInterval> | null = null;

  /**
   * Refresh interval
   */
  private readonly refreshInterval: number;

  /**
   * Change handlers registry
   */
  private readonly changeHandlers: Map<string, Set<ConfigChangeHandler>> = new Map();

  /**
   * Global change handlers (listen to all changes)
   */
  private readonly globalChangeHandlers: Set<ConfigChangeHandler> = new Set();

  /**
   * Enable change logging flag
   */
  private readonly enableChangeLogging: boolean;

  /**
   * Initialization state
   */
  private initialized = false;

  /**
   * Constructor
   *
   * @param options - Configuration options
   */
  constructor(options: ConfigCapabilityImplOptions) {
    const storeOptions: ConfigStoreOptions = {
      ttl: options.cacheTtl ?? 300000, // 5 minutes default
      initialConfig: options.initialConfig,
    };

    const httpClientOptions: ConfigHttpClientOptions = {
      httpCapability: options.httpCapability,
      endpoint: options.configEndpoint ?? '/api/v1/config',
      pluginId: options.pluginId,
    };

    this.store = new ConfigStore(storeOptions);
    this.httpClient = new ConfigHttpClient(httpClientOptions);
    this.refreshInterval = options.refreshInterval ?? 0;
    this.enableChangeLogging = options.enableChangeLogging ?? true;
  }

  /**
   * Initialize configuration capability
   * Load initial configuration from backend
   *
   * Initialization Flow:
   * 1. Load configuration from backend API
   * 2. Merge into configuration store
   * 3. Start auto-refresh (if refresh interval is configured)
   *
   * @returns Promise that resolves when initialization is complete
   */
  async initialize(): Promise<void> {
    if (this.initialized) {
      return;
    }

    try {
      // Load configuration from backend
      const remoteConfig = await this.httpClient.fetchConfig();
      this.store.merge(remoteConfig);

      // Start auto-refresh if configured
      if (this.refreshInterval > 0) {
        this.startAutoRefresh();
      }

      this.initialized = true;
    } catch (error) {
      // Log error but don't throw - use initial/default config
      
      // Mark as initialized even on failure to allow using default values
      this.initialized = true;
    }
  }

  /**
   * Get configuration value
   *
   * Supports dot-notation for nested keys (e.g., 'api.baseUrl')
   *
   * Key Resolution:
   * Supports dot-separated nested key access:
   * - 'api.baseUrl' -> config.api.baseUrl
   * - 'features.darkMode.enabled' -> config.features.darkMode.enabled
   *
   * @typeParam T - Expected value type
   * @param key - Configuration key (supports dot notation)
   * @param defaultValue - Default value if key not found
   * @returns Configuration value
   */
  get<T>(key: string, defaultValue?: T): T {
    const value = this.store.get<T>(key);
    
    if (value === undefined) {
      return defaultValue as T;
    }
    
    return value;
  }

  /**
   * Get all configuration
   *
   * @typeParam T - Expected configuration object type
   * @returns All configuration as an object
   */
  getAll<T = Record<string, unknown>>(): T {
    return this.store.getAll<T>();
  }

  /**
   * Refresh configuration from backend
   *
   * Refresh Flow:
   * 1. Fetch latest configuration from backend API
   * 2. Compare with existing configuration to detect changes
   * 3. Update configuration store
   * 4. Fire change events
   *
   * @returns Promise that resolves when refresh is complete
   */
  async refresh(): Promise<void> {
    try {
      const oldConfig = this.store.getAll();
      const newConfig = await this.httpClient.fetchConfig();
      
      // Detect changes and notify handlers
      const changes = this.detectChanges(oldConfig, newConfig);
      
      // Update store
      this.store.clear();
      this.store.merge(newConfig);
      
      // Notify change handlers
      for (const change of changes) {
        this.notifyChangeHandlers(change);
      }
    } catch (error) {
      throw error;
    }
  }

  /**
   * Register configuration change handler
   *
   * @param key - Configuration key to watch (use '*' for all changes)
   * @param handler - Change handler function
   * @returns Unsubscribe function
   */
  onConfigChange(key: string, handler: ConfigChangeHandler): () => void {
    if (key === '*') {
      this.globalChangeHandlers.add(handler);
      return () => {
        this.globalChangeHandlers.delete(handler);
      };
    }

    if (!this.changeHandlers.has(key)) {
      this.changeHandlers.set(key, new Set());
    }
    
    this.changeHandlers.get(key)!.add(handler);
    
    return () => {
      const handlers = this.changeHandlers.get(key);
      if (handlers) {
        handlers.delete(handler);
        if (handlers.size === 0) {
          this.changeHandlers.delete(key);
        }
      }
    };
  }

  /**
   * Set configuration value (local only, not persisted to backend)
   *
   * Local Configuration Override:
   * Used for runtime local config overrides, not persisted to backend.
   * Common for development debugging or temporary override scenarios.
   *
   * @param key - Configuration key
   * @param value - Configuration value
   */
  set(key: string, value: unknown): void {
    const oldValue = this.store.get(key);
    this.store.set(key, value);

    // Notify change handlers
    const changeEvent: ConfigChangeEvent = {
      key,
      oldValue,
      newValue: value,
      timestamp: Date.now(),
    };
    
    this.notifyChangeHandlers(changeEvent);
  }

  /**
   * Check if configuration key exists
   *
   * @param key - Configuration key
   * @returns True if key exists
   */
  has(key: string): boolean {
    return this.store.has(key);
  }

  /**
   * Destroy configuration capability
   * Clean up resources and stop auto-refresh
   *
   * Cleanup:
   * 1. Stop auto-refresh timer
   * 2. Clear change handlers
   * 3. Clear configuration store
   */
  destroy(): void {
    this.stopAutoRefresh();
    this.changeHandlers.clear();
    this.globalChangeHandlers.clear();
    this.store.clear();
    this.initialized = false;
  }

  /**
   * Start auto-refresh
   */
  private startAutoRefresh(): void {
    if (this.refreshTimer) {
      return;
    }

    this.refreshTimer = setInterval(() => {
      this.refresh().catch((_error) => {
      });
    }, this.refreshInterval);
  }

  /**
   * Stop auto-refresh
   */
  private stopAutoRefresh(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
      this.refreshTimer = null;
    }
  }

  /**
   * Detect configuration changes
   *
   * @param oldConfig - Previous configuration
   * @param newConfig - New configuration
   * @returns Array of change events
   */
  private detectChanges(
    oldConfig: Record<string, unknown>,
    newConfig: Record<string, unknown>,
  ): ConfigChangeEvent[] {
    const changes: ConfigChangeEvent[] = [];
    const timestamp = Date.now();

    // Flatten both configs for comparison
    const flatOld = this.flattenConfig(oldConfig);
    const flatNew = this.flattenConfig(newConfig);

    // Find changed and new keys
    for (const [key, newValue] of Object.entries(flatNew)) {
      const oldValue = flatOld[key];
      if (!this.deepEqual(oldValue, newValue)) {
        changes.push({ key, oldValue, newValue, timestamp });
      }
    }

    // Find deleted keys
    for (const key of Object.keys(flatOld)) {
      if (!(key in flatNew)) {
        changes.push({ key, oldValue: flatOld[key], newValue: undefined, timestamp });
      }
    }

    return changes;
  }

  /**
   * Flatten nested configuration object
   *
   * @param obj - Configuration object
   * @param prefix - Key prefix
   * @returns Flattened configuration
   */
  private flattenConfig(
    obj: Record<string, unknown>,
    prefix = '',
  ): Record<string, unknown> {
    const result: Record<string, unknown> = {};

    for (const [key, value] of Object.entries(obj)) {
      const fullKey = prefix ? `${prefix}.${key}` : key;

      if (value && typeof value === 'object' && !Array.isArray(value)) {
        Object.assign(result, this.flattenConfig(value as Record<string, unknown>, fullKey));
      } else {
        result[fullKey] = value;
      }
    }

    return result;
  }

  /**
   * Deep equality check
   *
   * @param a - First value
   * @param b - Second value
   * @returns True if values are deeply equal
   */
  private deepEqual(a: unknown, b: unknown): boolean {
    if (a === b) return true;
    
    if (typeof a !== typeof b) return false;
    
    if (typeof a !== 'object' || a === null || b === null) {
      return a === b;
    }

    if (Array.isArray(a) !== Array.isArray(b)) return false;

    if (Array.isArray(a) && Array.isArray(b)) {
      if (a.length !== b.length) return false;
      return a.every((item, index) => this.deepEqual(item, b[index]));
    }

    const keysA = Object.keys(a as object);
    const keysB = Object.keys(b as object);

    if (keysA.length !== keysB.length) return false;

    return keysA.every((key) =>
      this.deepEqual(
        (a as Record<string, unknown>)[key],
        (b as Record<string, unknown>)[key],
      ),
    );
  }

  /**
   * Notify change handlers
   *
   * @param event - Change event
   */
  private notifyChangeHandlers(event: ConfigChangeEvent): void {
    // Log change if enabled
    if (this.enableChangeLogging) {
      console.debug('[ConfigCapabilityImpl] Configuration changed:', event.key, {
        oldValue: event.oldValue,
        newValue: event.newValue,
      });
    }

    // Notify specific handlers
    const handlers = this.changeHandlers.get(event.key);
    if (handlers) {
      for (const handler of handlers) {
        try {
          handler(event);
        } catch (error) {
        }
      }
    }

    // Notify global handlers
    for (const handler of this.globalChangeHandlers) {
      try {
        handler(event);
      } catch (error) {
      }
    }
  }
}
