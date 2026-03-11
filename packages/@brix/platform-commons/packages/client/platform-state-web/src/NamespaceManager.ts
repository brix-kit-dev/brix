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
 * @file Namespace Manager
 * @description Manages plugin state namespaces
 * @module @brix/platform-state-web/NamespaceManager
 * @version 3.0.0
 * 
 * [Architecture Notes]
 * NamespaceManager is responsible for managing plugin state namespaces.
 * Ensures each plugin can only access its own state space, achieving state isolation.
 * 
 * [Naming Rules]
 * - State key format: {pluginId}:{localKey}
 * - Example: booking:filters, identity:currentUser
 * 
 * [Security]
 * - Plugins cannot access other plugins' state
 * - Namespace is specified by Host when creating capability instance
 * - Keys in plugin code automatically get namespace prefix added
 */

/**
 * Namespace information
 */
export interface NamespaceInfo {
  /**
   * Namespace ID (usually plugin ID)
   */
  id: string;
  
  /**
   * Creation timestamp
   */
  createdAt: number;
  
  /**
   * State count
   */
  stateCount: number;
}

/**
 * Namespace Manager
 * 
 * Manages plugin state namespaces, achieving state isolation.
 * 
 * [Usage Example]
 * ```typescript
 * const manager = new NamespaceManager();
 * 
 * // Register namespace
 * manager.register('booking');
 * manager.register('identity');
 * 
 * // Build full key
 * const fullKey = manager.buildKey('booking', 'filters');
 * // 'booking:filters'
 * 
 * // Parse namespace
 * const [namespace, localKey] = manager.parseKey('booking:filters');
 * // ['booking', 'filters']
 * 
 * // Validate access permission
 * manager.validateAccess('booking', 'booking:filters'); // true
 * manager.validateAccess('booking', 'identity:user');   // false
 * ```
 */
export class NamespaceManager {
  /**
   * Registered namespaces
   */
  private namespaces: Map<string, NamespaceInfo> = new Map();
  
  /**
   * Namespace separator
   */
  private readonly separator = ':';
  
  /**
   * Register namespace
   * 
   * Called by Host when loading plugin.
   * 
   * @param namespace - Namespace ID
   * @throws Throws error if namespace already exists
   */
  register(namespace: string): void {
    if (this.namespaces.has(namespace)) {
      throw new Error(`[NamespaceManager] Namespace already exists: ${namespace}`);
    }
    
    this.namespaces.set(namespace, {
      id: namespace,
      createdAt: Date.now(),
      stateCount: 0,
    });
  }
  
  /**
   * Unregister namespace
   * 
   * Called by Host when unloading plugin.
   * 
   * @param namespace - Namespace ID
   */
  unregister(namespace: string): void {
    this.namespaces.delete(namespace);
  }
  
  /**
   * Check if namespace is registered
   * 
   * @param namespace - Namespace ID
   * @returns Whether registered
   */
  isRegistered(namespace: string): boolean {
    return this.namespaces.has(namespace);
  }
  
  /**
   * Build full state key
   * 
   * Adds namespace prefix to local key.
   * 
   * @param namespace - Namespace
   * @param localKey - Local key name
   * @returns Full key name
   */
  buildKey(namespace: string, localKey: string): string {
    return `${namespace}${this.separator}${localKey}`;
  }
  
  /**
   * Parse state key
   * 
   * Extracts namespace and local key from full key.
   * 
   * @param fullKey - Full key name
   * @returns [namespace, localKey] tuple
   */
  parseKey(fullKey: string): [string, string] {
    const separatorIndex = fullKey.indexOf(this.separator);
    
    if (separatorIndex === -1) {
      return ['', fullKey];
    }
    
    return [
      fullKey.slice(0, separatorIndex),
      fullKey.slice(separatorIndex + 1),
    ];
  }
  
  /**
   * Get namespace of key
   * 
   * @param fullKey - Full key name
   * @returns Namespace
   */
  getNamespace(fullKey: string): string {
    return this.parseKey(fullKey)[0];
  }
  
  /**
   * Get local name of key
   * 
   * @param fullKey - Full key name
   * @returns Local key name
   */
  getLocalKey(fullKey: string): string {
    return this.parseKey(fullKey)[1];
  }
  
  /**
   * Validate access permission
   * 
   * Checks if specified namespace has permission to access target key.
   * 
   * @param namespace - Namespace requesting access
   * @param fullKey - Target key name
   * @returns Whether access is permitted
   */
  validateAccess(namespace: string, fullKey: string): boolean {
    const keyNamespace = this.getNamespace(fullKey);
    
    // Can only access state in own namespace
    return keyNamespace === namespace;
  }
  
  /**
   * Get all registered namespaces
   * 
   * @returns Namespace info array
   */
  getAll(): NamespaceInfo[] {
    return Array.from(this.namespaces.values());
  }
  
  /**
   * Get namespace information
   * 
   * @param namespace - Namespace ID
   * @returns Namespace info, undefined if not exists
   */
  getInfo(namespace: string): NamespaceInfo | undefined {
    return this.namespaces.get(namespace);
  }
  
  /**
   * Update namespace state count
   * 
   * @param namespace - Namespace ID
   * @param delta - Change amount (can be negative)
   */
  updateStateCount(namespace: string, delta: number): void {
    const info = this.namespaces.get(namespace);
    if (info) {
      info.stateCount = Math.max(0, info.stateCount + delta);
    }
  }
  
  /**
   * Clear all namespaces
   */
  clear(): void {
    this.namespaces.clear();
  }
}
