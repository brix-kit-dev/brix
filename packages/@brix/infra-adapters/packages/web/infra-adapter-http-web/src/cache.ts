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
 * @file cache.ts
 * @description HTTP response caching with TTL and LFU eviction
 * @module @brix-sdk/infra-adapter-http-web
 * @author Brix Platform Team
 * @version 3.1.0
 * 
 * This module provides a simple and efficient in-memory cache implementation with:
 * - TTL (Time To Live) expiration mechanism
 * - LFU (Least Frequently Used) eviction strategy
 * - Automatic expiration cleanup
 * - Batch clearing by prefix/pattern
 * 
 * 【架构说明】
 * 本模块属于 v3.0 架构的基础设施适配层(Layer 2.5)，
 * 提供与具体 HTTP 库解耦的通用缓存能力。
 * 
 * Use Cases:
 * - Cache HTTP responses to reduce duplicate requests
 * - Cache computation results to improve performance
 * - Temporary data storage
 * 
 * Notes:
 * - This is in-memory cache, data is lost after process restart
 * - Suitable for single-process scenarios; for multi-process, use Redis or other distributed caches
 */

// ============================================================
// Cache Configuration
// ============================================================

/**
 * Cache options.
 * 
 * Configuration parameters for cache behavior.
 */
export interface CacheOptions {
  /**
   * Default TTL (milliseconds).
   * 
   * Default expiration time used when TTL is not specified.
   * @default 60000 (1 minute)
   */
  defaultTTL: number;

  /**
   * Maximum cache entries.
   * 
   * LFU eviction will be triggered when this number is exceeded.
   * @default 100
   */
  maxSize: number;

  /**
   * Whether to enable expiration cleanup.
   * 
   * When enabled, expired entries will be periodically cleaned up.
   * @default true
   */
  enableCleanup: boolean;

  /**
   * Cleanup interval (milliseconds).
   * 
   * Interval for periodic cleanup of expired entries.
   * @default 60000 (1 minute)
   */
  cleanupInterval: number;
}

/**
 * Default cache options.
 */
export const DEFAULT_CACHE_OPTIONS: CacheOptions = {
  defaultTTL: 60000,
  maxSize: 100,
  enableCleanup: true,
  cleanupInterval: 60000,
};

// ============================================================
// Cache Entry
// ============================================================

/**
 * Cache entry (internal use).
 * 
 * Stores cache data and its metadata.
 * 
 * @typeParam T - Cache data type
 */
interface CacheEntry<T> {
  /**
   * Cached data.
   */
  data: T;

  /**
   * Expiration timestamp.
   */
  expiry: number;

  /**
   * Creation timestamp.
   */
  createdAt: number;

  /**
   * Access count (for LFU eviction).
   */
  hits: number;
}

// ============================================================
// SimpleCache Class
// ============================================================

/**
 * Simple in-memory cache.
 * 
 * Provides TTL-based key-value caching with automatic expiration cleanup
 * and LFU eviction strategy.
 * 
 * 【关键特性】
 * - 泛型支持，类型安全
 * - 自动过期清理
 * - LFU 驱逐（空间不足时删除访问次数最少的条目）
 * - 按前缀/模式批量清除
 * - 统计信息查询
 * 
 * Features:
 * - Generic support, type-safe
 * - Automatic expiration cleanup
 * - LFU eviction (deletes least accessed entries when space is insufficient)
 * - Batch clearing by prefix/pattern
 * - Statistics query
 * 
 * @example
 * ```typescript
 * // Create cache instance
 * const cache = new SimpleCache({
 *   defaultTTL: 60000,  // Default 1 minute expiration
 *   maxSize: 100        // Maximum 100 entries
 * });
 * 
 * // Set cache
 * cache.set('users:list', userData);
 * cache.set('users:1', singleUser, 30000);  // 30 seconds expiration
 * 
 * // Get cache
 * const users = cache.get<User[]>('users:list');
 * 
 * // Check cache
 * if (cache.has('users:list')) {
 *   console.log('Cache exists');
 * }
 * 
 * // Clear by prefix
 * cache.clearByPrefix('users:');
 * 
 * // Destroy instance
 * cache.destroy();
 * ```
 */
export class SimpleCache {
  /**
   * Cache storage (implemented using Map internally).
   */
  private cache = new Map<string, CacheEntry<unknown>>();

  /**
   * Cache options.
   */
  private options: CacheOptions;

  /**
   * Cleanup timer.
   */
  private cleanupTimer: ReturnType<typeof setInterval> | null = null;

  /**
   * Creates a cache instance.
   * 
   * @param options - Cache options, using defaults for unprovided options
   */
  constructor(options: Partial<CacheOptions> = {}) {
    this.options = { ...DEFAULT_CACHE_OPTIONS, ...options };

    if (this.options.enableCleanup) {
      this.startCleanup();
    }
  }

  /**
   * Gets a cached value.
   * 
   * Returns null if cache doesn't exist or has expired.
   * 
   * @typeParam T - Cache data type
   * @param key - Cache key
   * @returns Cached value or null
   * 
   * @example
   * ```typescript
   * const user = cache.get<User>('user:1');
   * if (user) {
   *   console.log(user.name);
   * }
   * ```
   */
  get<T>(key: string): T | null {
    const entry = this.cache.get(key);

    if (!entry) {
      return null;
    }

    // Check if expired
    if (Date.now() > entry.expiry) {
      this.cache.delete(key);
      return null;
    }

    // Update access count (LFU statistics)
    entry.hits++;

    return entry.data as T;
  }

  /**
   * Sets a cache value.
   * 
   * LFU eviction will be triggered if cache is full.
   * 
   * @typeParam T - Cache data type
   * @param key - Cache key
   * @param data - Cache data
   * @param ttlMs - TTL (milliseconds), defaults to configured defaultTTL
   * 
   * @example
   * ```typescript
   * // Using default TTL
   * cache.set('user:1', userData);
   * 
   * // Specifying TTL
   * cache.set('token', accessToken, 3600000);  // 1 hour
   * ```
   */
  set<T>(key: string, data: T, ttlMs?: number): void {
    // Check if eviction is needed
    if (this.cache.size >= this.options.maxSize) {
      this.evict();
    }

    const now = Date.now();
    this.cache.set(key, {
      data,
      expiry: now + (ttlMs ?? this.options.defaultTTL),
      createdAt: now,
      hits: 0,
    });
  }

  /**
   * Checks if cache exists.
   * 
   * Checks expiration status; expired cache will be automatically deleted and returns false.
   * 
   * @param key - Cache key
   * @returns Whether valid cache exists
   */
  has(key: string): boolean {
    const entry = this.cache.get(key);
    if (!entry) return false;

    if (Date.now() > entry.expiry) {
      this.cache.delete(key);
      return false;
    }

    return true;
  }

  /**
   * Deletes cache.
   * 
   * @param key - Cache key
   * @returns Whether deletion was successful
   */
  delete(key: string): boolean {
    return this.cache.delete(key);
  }

  /**
   * Clears all cache.
   */
  clear(): void {
    this.cache.clear();
  }

  /**
   * Clears cache by prefix.
   * 
   * Deletes all cache entries starting with the specified prefix.
   * 
   * @param prefix - Key prefix
   * 
   * @example
   * ```typescript
   * // Clear all user-related cache
   * cache.clearByPrefix('user:');
   * ```
   */
  clearByPrefix(prefix: string): void {
    for (const key of this.cache.keys()) {
      if (key.startsWith(prefix)) {
        this.cache.delete(key);
      }
    }
  }

  /**
   * Clears cache by pattern.
   * 
   * Deletes all cache entries matching the regular expression.
   * 
   * @param pattern - Regular expression pattern
   * 
   * @example
   * ```typescript
   * // Clear all user caches with numeric IDs
   * cache.clearByPattern(/^user:\d+$/);
   * ```
   */
  clearByPattern(pattern: RegExp): void {
    for (const key of this.cache.keys()) {
      if (pattern.test(key)) {
        this.cache.delete(key);
      }
    }
  }

  /**
   * Gets cache size.
   * 
   * Returns current number of cache entries (including expired but not yet cleaned).
   */
  get size(): number {
    return this.cache.size;
  }

  /**
   * Gets all cache keys.
   * 
   * @returns Array of cache keys
   */
  keys(): string[] {
    return Array.from(this.cache.keys());
  }

  /**
   * Gets cache statistics.
   * 
   * @returns Statistics object
   * 
   * @example
   * ```typescript
   * const stats = cache.stats();
   * console.log(`Cache entries: ${stats.size}`);
   * console.log(`Total hits: ${stats.totalHits}`);
   * ```
   */
  stats(): {
    /** Current number of cache entries */
    size: number;
    /** All cache keys */
    keys: string[];
    /** Total access count */
    totalHits: number;
    /** Oldest entry creation timestamp */
    oldestEntry: number | null;
  } {
    let totalHits = 0;
    let oldestEntry: number | null = null;

    for (const entry of this.cache.values()) {
      totalHits += entry.hits;
      if (oldestEntry === null || entry.createdAt < oldestEntry) {
        oldestEntry = entry.createdAt;
      }
    }

    return {
      size: this.cache.size,
      keys: this.keys(),
      totalHits,
      oldestEntry,
    };
  }

  /**
   * Destroys the cache instance.
   * 
   * Stops the automatic cleanup timer and clears all cache, releasing resources.
   * The instance should not be used after destruction.
   */
  destroy(): void {
    this.stopCleanup();
    this.clear();
  }

  /**
   * Evicts cache entries (internal method).
   * 
   * Called when cache is full, uses LFU (Least Frequently Used) strategy:
   * 1. First delete all expired entries
   * 2. If still over limit, delete the least accessed entry
   * 
   * Uses single-pass traversal for performance optimization.
   */
  private evict(): void {
    const now = Date.now();
    const keysToDelete: string[] = [];
    let minHits = Infinity;
    let minKey: string | null = null;

    // Single-pass traversal: handle expired entries and find least used entry simultaneously
    for (const [key, entry] of this.cache.entries()) {
      if (now > entry.expiry) {
        keysToDelete.push(key);
      } else if (entry.hits < minHits) {
        minHits = entry.hits;
        minKey = key;
      }
    }

    // Delete expired entries
    for (const key of keysToDelete) {
      this.cache.delete(key);
    }

    // If still over limit, delete least used entry
    if (this.cache.size >= this.options.maxSize && minKey) {
      this.cache.delete(minKey);
    }
  }

  /**
   * Starts automatic cleanup (internal method).
   * 
   * Periodically traverses cache and deletes expired entries.
   */
  private startCleanup(): void {
    this.cleanupTimer = setInterval(() => {
      const now = Date.now();
      for (const [key, entry] of this.cache.entries()) {
        if (now > entry.expiry) {
          this.cache.delete(key);
        }
      }
    }, this.options.cleanupInterval);
  }

  /**
   * Stops automatic cleanup (internal method).
   */
  private stopCleanup(): void {
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
      this.cleanupTimer = null;
    }
  }
}

// ============================================================
// Cache Utility Functions
// ============================================================

/**
 * Generates a cache key.
 * 
 * Generates a unique cache key based on URL and parameters. Parameters are sorted
 * by key to ensure consistency.
 * 
 * @param url - Request URL
 * @param params - Request parameters
 * @returns Cache key
 * 
 * @example
 * ```typescript
 * const key = generateCacheKey('/api/users', { page: 1, size: 10 });
 * // Result: '/api/users?page=1&size=10'
 * ```
 */
export function generateCacheKey(
  url: string,
  params?: Record<string, unknown>
): string {
  const sortedParams = params
    ? Object.keys(params)
        .sort()
        .map((key) => `${key}=${JSON.stringify(params[key])}`)
        .join('&')
    : '';

  return sortedParams ? `${url}?${sortedParams}` : url;
}

/**
 * Async function wrapper with caching.
 * 
 * Automatically caches async function execution results. Subsequent calls
 * directly return the cached value.
 * 
 * 【关键特性】
 * - 自动缓存异步函数结果
 * - 支持自定义 TTL
 * - 缓存命中时直接返回，不执行函数
 * 
 * @typeParam T - Return value type
 * @param key - Cache key
 * @param fn - Async function to execute
 * @param cache - Cache instance
 * @param ttl - Cache TTL (optional)
 * @returns Promise with execution result
 * 
 * @example
 * ```typescript
 * const cache = new SimpleCache();
 * 
 * // First call executes the function and caches the result
 * const users = await withCache(
 *   'users:list',
 *   () => fetch('/api/users').then(r => r.json()),
 *   cache,
 *   30000  // 30 second cache
 * );
 * 
 * // Second call directly returns cache
 * const cachedUsers = await withCache(
 *   'users:list',
 *   () => fetch('/api/users').then(r => r.json()),
 *   cache
 * );
 * ```
 */
export async function withCache<T>(
  key: string,
  fn: () => Promise<T>,
  cache: SimpleCache,
  ttl?: number
): Promise<T> {
  // Check cache
  const cached = cache.get<T>(key);
  if (cached !== null) {
    return cached;
  }

  // Execute function
  const result = await fn();

  // Store in cache
  cache.set(key, result, ttl);

  return result;
}
