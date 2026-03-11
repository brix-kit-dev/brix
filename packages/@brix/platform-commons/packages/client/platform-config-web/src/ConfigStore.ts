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
 * @file Configuration Store
 * @description In-memory configuration store with TTL support
 * @module @brix/platform-config-web/ConfigStore
 * @version 3.1.0
 *
 * 【功能说明】
 * ConfigStore 是一个内存配置存储，支持：
 * 1. 嵌套键访问（点号分隔）
 * 2. TTL 过期机制
 * 3. 配置合并
 */

/**
 * Configuration Store Options
 */
export interface ConfigStoreOptions {
  /**
   * Cache TTL in milliseconds
   * 缓存 TTL（毫秒）
   * @default 300000 (5 minutes)
   */
  ttl?: number;

  /**
   * Initial configuration
   * 初始配置
   */
  initialConfig?: Record<string, unknown>;
}

/**
 * Cache entry with expiration
 * 带过期时间的缓存条目
 */
interface CacheEntry {
  /**
   * Cached value
   */
  value: unknown;
  
  /**
   * Expiration timestamp (0 means never expires)
   */
  expiresAt: number;
}

/**
 * Configuration Store
 *
 * In-memory configuration store with support for nested keys and TTL.
 *
 * 【存储结构】
 * 配置以扁平化键值对存储，支持点号分隔的嵌套访问
 * 例如：'api.baseUrl' -> { key: 'api.baseUrl', value: '/api/v1' }
 *
 * Usage Example:
 * ```typescript
 * const store = new ConfigStore({ ttl: 60000 });
 * store.set('api.baseUrl', '/api/v1');
 * const value = store.get<string>('api.baseUrl'); // '/api/v1'
 * ```
 */
export class ConfigStore {
  /**
   * Configuration cache
   * 配置缓存
   */
  private readonly cache: Map<string, CacheEntry> = new Map();

  /**
   * Raw configuration object (for getAll)
   * 原始配置对象（用于 getAll）
   */
  private configObject: Record<string, unknown> = {};

  /**
   * Default TTL
   * 默认 TTL
   */
  private readonly defaultTtl: number;

  /**
   * Constructor
   *
   * @param options - Store options
   */
  constructor(options: ConfigStoreOptions = {}) {
    this.defaultTtl = options.ttl ?? 300000; // 5 minutes default

    if (options.initialConfig) {
      this.merge(options.initialConfig);
    }
  }

  /**
   * Get configuration value by key
   *
   * Supports dot-notation for nested keys.
   *
   * 【键值解析逻辑】
   * 1. 首先尝试直接匹配完整键
   * 2. 如果没找到，尝试从原始对象中解析嵌套路径
   *
   * @typeParam T - Expected value type
   * @param key - Configuration key (supports dot notation)
   * @returns Configuration value or undefined
   */
  get<T>(key: string): T | undefined {
    // Check cache first
    const cached = this.cache.get(key);
    
    if (cached) {
      // Check expiration
      if (cached.expiresAt > 0 && Date.now() > cached.expiresAt) {
        this.cache.delete(key);
        return undefined;
      }
      return cached.value as T;
    }

    // Try to resolve from config object
    return this.resolveNestedKey<T>(key);
  }

  /**
   * Set configuration value
   *
   * @param key - Configuration key
   * @param value - Configuration value
   * @param ttl - Optional TTL in milliseconds (overrides default)
   */
  set(key: string, value: unknown, ttl?: number): void {
    const expiresAt = ttl === 0 ? 0 : Date.now() + (ttl ?? this.defaultTtl);
    
    this.cache.set(key, { value, expiresAt });
    
    // Also update config object
    this.setNestedKey(key, value);
  }

  /**
   * Check if key exists
   *
   * @param key - Configuration key
   * @returns True if key exists and not expired
   */
  has(key: string): boolean {
    const cached = this.cache.get(key);
    
    if (cached) {
      if (cached.expiresAt > 0 && Date.now() > cached.expiresAt) {
        this.cache.delete(key);
        return false;
      }
      return true;
    }

    return this.resolveNestedKey(key) !== undefined;
  }

  /**
   * Delete configuration key
   *
   * @param key - Configuration key
   */
  delete(key: string): void {
    this.cache.delete(key);
    this.deleteNestedKey(key);
  }

  /**
   * Clear all configuration
   */
  clear(): void {
    this.cache.clear();
    this.configObject = {};
  }

  /**
   * Get all configuration as object
   *
   * @typeParam T - Expected configuration type
   * @returns Configuration object
   */
  getAll<T = Record<string, unknown>>(): T {
    return this.configObject as T;
  }

  /**
   * Merge configuration
   *
   * Deep merge new configuration into existing configuration.
   *
   * 【合并策略】
   * 深度合并：对象会递归合并，数组和原始值会被覆盖
   *
   * @param config - Configuration to merge
   */
  merge(config: Record<string, unknown>): void {
    this.configObject = this.deepMerge(this.configObject, config);
    
    // Update cache with flattened keys
    const flattened = this.flattenConfig(config);
    for (const [key, value] of Object.entries(flattened)) {
      this.cache.set(key, {
        value,
        expiresAt: Date.now() + this.defaultTtl,
      });
    }
  }

  /**
   * Resolve nested key from config object
   *
   * @param key - Dot-separated key
   * @returns Value or undefined
   */
  private resolveNestedKey<T>(key: string): T | undefined {
    const parts = key.split('.');
    let current: unknown = this.configObject;

    for (const part of parts) {
      if (current === null || current === undefined) {
        return undefined;
      }
      
      if (typeof current !== 'object') {
        return undefined;
      }
      
      current = (current as Record<string, unknown>)[part];
    }

    return current as T;
  }

  /**
   * Set nested key in config object
   *
   * @param key - Dot-separated key
   * @param value - Value to set
   */
  private setNestedKey(key: string, value: unknown): void {
    const parts = key.split('.');
    let current: Record<string, unknown> = this.configObject;

    for (let i = 0; i < parts.length - 1; i++) {
      const part = parts[i];
      if (!(part in current) || typeof current[part] !== 'object') {
        current[part] = {};
      }
      current = current[part] as Record<string, unknown>;
    }

    current[parts[parts.length - 1]] = value;
  }

  /**
   * Delete nested key from config object
   *
   * @param key - Dot-separated key
   */
  private deleteNestedKey(key: string): void {
    const parts = key.split('.');
    let current: Record<string, unknown> = this.configObject;

    for (let i = 0; i < parts.length - 1; i++) {
      const part = parts[i];
      if (!(part in current) || typeof current[part] !== 'object') {
        return;
      }
      current = current[part] as Record<string, unknown>;
    }

    delete current[parts[parts.length - 1]];
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
        Object.assign(
          result,
          this.flattenConfig(value as Record<string, unknown>, fullKey),
        );
      } else {
        result[fullKey] = value;
      }
    }

    return result;
  }

  /**
   * Deep merge two objects
   *
   * @param target - Target object
   * @param source - Source object
   * @returns Merged object
   */
  private deepMerge(
    target: Record<string, unknown>,
    source: Record<string, unknown>,
  ): Record<string, unknown> {
    const result = { ...target };

    for (const [key, value] of Object.entries(source)) {
      if (
        value &&
        typeof value === 'object' &&
        !Array.isArray(value) &&
        result[key] &&
        typeof result[key] === 'object' &&
        !Array.isArray(result[key])
      ) {
        result[key] = this.deepMerge(
          result[key] as Record<string, unknown>,
          value as Record<string, unknown>,
        );
      } else {
        result[key] = value;
      }
    }

    return result;
  }
}
