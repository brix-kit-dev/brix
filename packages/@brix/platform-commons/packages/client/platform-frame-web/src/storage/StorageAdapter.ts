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
 * @file Storage Adapter Interface
 * @description Abstract storage operations, supporting injection of different implementations
 * @module @brix-sdk/platform-frame-web/storage/StorageAdapter
 * @version 3.2.0
 * 
 * [Design Notes]
 * Abstract storage operations, supporting injection of different implementations (localStorage, PluginStateCapability, etc.).
 * Shell layer doesn't directly depend on browser APIs, but achieves decoupling through the adapter pattern.
 * 
 * [Architecture Principles]
 * - Dependency Inversion: Upper modules depend on abstract interfaces, not concrete implementations
 * - Testability: Easy to inject Mock implementations in unit tests
 * - Extensibility: Supports future replacement with other storage backends
 */

/**
 * Storage Adapter Interface
 * 
 * Defines a unified storage operation interface supporting different storage backend implementations.
 * 
 * @example
 * ```typescript
 * // Use default localStorage adapter
 * import { defaultStorage } from './storage';
 * 
 * defaultStorage.set('user', { name: 'Alice' });
 * const user = defaultStorage.get<{ name: string }>('user');
 * ```
 */
export interface StorageAdapter {
  /**
   * Get stored value
   * 
   * @typeParam T - Value type
   * @param key - Storage key name
   * @returns Stored value, returns null if not exists
   */
  get<T>(key: string): T | null;
  
  /**
   * Set stored value
   * 
   * @typeParam T - Value type
   * @param key - Storage key name
   * @param value - Value to store
   */
  set<T>(key: string, value: T): void;
  
  /**
   * Remove stored item
   * 
   * @param key - Storage key name
   */
  remove(key: string): void;
  
  /**
   * Check if stored item exists
   * 
   * @param key - Storage key name
   * @returns Whether exists
   */
  has(key: string): boolean;
}

/**
 * localStorage Adapter Implementation
 * 
 * localStorage-based storage adapter implementation.
 * Supports automatic JSON serialization/deserialization and key name prefixing.
 * 
 * @example
 * ```typescript
 * const storage = new LocalStorageAdapter('myApp');
 * storage.set('config', { theme: 'dark' });
 * // Actual storage key name is 'myApp:config'
 * ```
 */
export class LocalStorageAdapter implements StorageAdapter {
  /**
   * Constructor
   * 
   * @param prefix - Key name prefix, used to isolate storage of different apps/modules
   */
  constructor(private readonly prefix: string = '') {}
  
  /**
   * Generate complete storage key name
   * 
   * @param key - Original key name
   * @returns Complete key name with prefix
   */
  private getKey(key: string): string {
    return this.prefix ? `${this.prefix}:${key}` : key;
  }
  
  /**
   * Get stored value
   * 
   * @typeParam T - Value type
   * @param key - Storage key name
   * @returns Parsed value, returns null if not exists or parse fails
   */
  get<T>(key: string): T | null {
    if (typeof localStorage === 'undefined') return null;
    try {
      const data = localStorage.getItem(this.getKey(key));
      return data ? JSON.parse(data) : null;
    } catch {
      return null;
    }
  }
  
  /**
   * Set stored value
   * 
   * @typeParam T - Value type
   * @param key - Storage key name
   * @param value - Value to store (will be auto JSON serialized)
   */
  set<T>(key: string, value: T): void {
    if (typeof localStorage === 'undefined') return;
    try {
      localStorage.setItem(this.getKey(key), JSON.stringify(value));
    } catch (e) {
      console.warn('[LocalStorageAdapter] save failed:', e);
    }
  }
  
  /**
   * Remove stored item
   * 
   * @param key - Storage key name
   */
  remove(key: string): void {
    if (typeof localStorage === 'undefined') return;
    localStorage.removeItem(this.getKey(key));
  }
  
  /**
   * Check if stored item exists
   * 
   * @param key - Storage key name
   * @returns Whether exists
   */
  has(key: string): boolean {
    if (typeof localStorage === 'undefined') return false;
    return localStorage.getItem(this.getKey(key)) !== null;
  }
}

/**
 * Default storage instance
 * 
 * Uses 'brix' as key prefix to avoid conflicts with other applications.
 */
export const defaultStorage = new LocalStorageAdapter('brix');
