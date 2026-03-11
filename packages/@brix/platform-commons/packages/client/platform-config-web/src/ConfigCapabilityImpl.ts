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
 * @module @brix/platform-config-web/ConfigCapabilityImpl
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
 * 【架构要点】
 * - 配置能力通过 HttpCapability 从后端拉取配置，不直接调用 fetch
 * - 支持配置热更新，通过轮询或推送机制实现
 * - 配置缓存在内存中，支持 TTL 过期
 */

import type { 
  ConfigCapability,
  HttpCapability,
} from '@brix/runtime-sdk-api-web';
import { ConfigStore, type ConfigStoreOptions } from './ConfigStore';
import { ConfigHttpClient, type ConfigHttpClientOptions } from './ConfigHttpClient';

/**
 * Configuration Capability Implementation Options
 *
 * 【配置项说明】
 * - httpCapability: HTTP 能力实例，用于从后端拉取配置
 * - configEndpoint: 配置 API 端点
 * - refreshInterval: 配置刷新间隔（毫秒），0 表示禁用自动刷新
 * - initialConfig: 初始配置（用于 SSR 或预加载场景）
 */
export interface ConfigCapabilityImplOptions {
  /**
   * HTTP Capability instance for fetching configuration
   * HTTP 能力实例，用于从后端拉取配置
   */
  httpCapability: HttpCapability;

  /**
   * Configuration API endpoint
   * 配置 API 端点 URL
   * @default '/api/v1/config'
   */
  configEndpoint?: string;

  /**
   * Configuration refresh interval in milliseconds
   * Set to 0 to disable auto-refresh
   * 配置自动刷新间隔（毫秒），设为 0 禁用自动刷新
   * @default 0
   */
  refreshInterval?: number;

  /**
   * Initial configuration (for SSR or preloading scenarios)
   * 初始配置（用于 SSR 或预加载场景）
   */
  initialConfig?: Record<string, unknown>;

  /**
   * Plugin ID for scoped configuration
   * 插件 ID，用于获取作用域配置
   */
  pluginId?: string;

  /**
   * Cache TTL in milliseconds
   * 缓存 TTL（毫秒）
   * @default 300000 (5 minutes)
   */
  cacheTtl?: number;

  /**
   * Enable configuration change logging
   * 启用配置变更日志
   * @default true
   */
  enableChangeLogging?: boolean;
}

/**
 * Configuration change event
 * 配置变更事件
 */
export interface ConfigChangeEvent {
  /**
   * Changed configuration key
   */
  key: string;
  
  /**
   * Previous value
   */
  oldValue: unknown;
  
  /**
   * New value
   */
  newValue: unknown;
  
  /**
   * Change timestamp
   */
  timestamp: number;
}

/**
 * Configuration change handler type
 */
export type ConfigChangeHandler = (event: ConfigChangeEvent) => void;

/**
 * Configuration Capability Implementation
 *
 * Implements ConfigCapability interface, providing configuration management
 * with support for remote loading, caching, and hot-reload.
 *
 * 【核心实现说明】
 * 1. 通过 HttpCapability 从后端 API 加载配置
 * 2. 配置缓存在 ConfigStore 中，支持 TTL 过期
 * 3. 支持配置变更监听，可用于实现热更新
 * 4. 支持点号分隔的嵌套键访问，如 'api.baseUrl'
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
export class ConfigCapabilityImpl implements ConfigCapability {
  /**
   * Configuration store
   * 配置存储
   */
  private readonly store: ConfigStore;

  /**
   * Configuration HTTP client
   * 配置 HTTP 客户端
   */
  private readonly httpClient: ConfigHttpClient;

  /**
   * Auto-refresh timer
   * 自动刷新定时器
   */
  private refreshTimer: ReturnType<typeof setInterval> | null = null;

  /**
   * Refresh interval
   * 刷新间隔
   */
  private readonly refreshInterval: number;

  /**
   * Change handlers registry
   * 变更处理器注册表
   */
  private readonly changeHandlers: Map<string, Set<ConfigChangeHandler>> = new Map();

  /**
   * Global change handlers (listen to all changes)
   * 全局变更处理器（监听所有变更）
   */
  private readonly globalChangeHandlers: Set<ConfigChangeHandler> = new Set();

  /**
   * Enable change logging flag
   * 启用变更日志标志
   */
  private readonly enableChangeLogging: boolean;

  /**
   * Initialization state
   * 初始化状态
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
   * 【初始化流程】
   * 1. 从后端 API 加载配置
   * 2. 合并到配置存储
   * 3. 启动自动刷新（如果配置了刷新间隔）
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
      console.error('[ConfigCapabilityImpl] Failed to load remote configuration:', error);
      
      // Mark as initialized even on failure to allow using default values
      this.initialized = true;
    }
  }

  /**
   * Get configuration value
   *
   * Supports dot-notation for nested keys (e.g., 'api.baseUrl')
   *
   * 【键值解析】
   * 支持点号分隔的嵌套键访问：
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
   * 【刷新流程】
   * 1. 从后端 API 获取最新配置
   * 2. 对比现有配置，检测变更
   * 3. 更新配置存储
   * 4. 触发变更事件
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
      console.error('[ConfigCapabilityImpl] Failed to refresh configuration:', error);
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
   * 【本地配置覆盖】
   * 用于运行时本地覆盖配置，不持久化到后端
   * 常用于开发调试或临时覆盖场景
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
   * 【清理工作】
   * 1. 停止自动刷新定时器
   * 2. 清空变更处理器
   * 3. 清空配置存储
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
   * 启动自动刷新
   */
  private startAutoRefresh(): void {
    if (this.refreshTimer) {
      return;
    }

    this.refreshTimer = setInterval(() => {
      this.refresh().catch((error) => {
        console.error('[ConfigCapabilityImpl] Auto-refresh failed:', error);
      });
    }, this.refreshInterval);
  }

  /**
   * Stop auto-refresh
   * 停止自动刷新
   */
  private stopAutoRefresh(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
      this.refreshTimer = null;
    }
  }

  /**
   * Detect configuration changes
   * 检测配置变更
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
   * 展平嵌套配置对象
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
   * 深度相等比较
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
   * 通知变更处理器
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
          console.error('[ConfigCapabilityImpl] Change handler error:', error);
        }
      }
    }

    // Notify global handlers
    for (const handler of this.globalChangeHandlers) {
      try {
        handler(event);
      } catch (error) {
        console.error('[ConfigCapabilityImpl] Global change handler error:', error);
      }
    }
  }
}
