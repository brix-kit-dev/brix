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
 * @file Configuration capability type definitions
 * @description Define core types for configuration management system
 * @module @brix-sdk/runtime-sdk-api-mobile/types/config
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Maintains consistent configuration capability type definitions with runtime-sdk-api-web.
 */

// =========================================
// Configuration Capability Type Identifier
// =========================================

/**
 * Configuration Capability Type Identifier
 */
export const ConfigCapabilityType = Symbol.for('ConfigCapability');

// =========================================
// Configuration Capability Contract
// =========================================

/**
 * Configuration Capability Contract
 *
 * <p>Provides runtime configuration reading capability for plugins.</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const config = context.getCapability<ConfigCapability>(ConfigCapabilityType);
 * const apiBase = config.get<string>('api.baseUrl', '/api/v1');
 * const timeout = config.get<number>('http.timeout', 30000);
 * ```
 *
 * <h3>Configuration Sources</h3>
 * <ul>
 *   <li>Environment variables</li>
 *   <li>Configuration center</li>
 *   <li>Manifest files</li>
 *   <li>Mobile: Native config, App Bundle config</li>
 * </ul>
 */
export interface ConfigCapability {
  /**
   * Get configuration item
   *
   * @param key Configuration key
   * @param defaultValue Default value
   * @returns Configuration value
   */
  get<T>(key: string, defaultValue?: T): T;

  /**
   * Get all configurations
   *
   * @returns Configuration object (supports sync/async)
   */
  getAll<T = Record<string, unknown>>(): T | Promise<T>;
}
