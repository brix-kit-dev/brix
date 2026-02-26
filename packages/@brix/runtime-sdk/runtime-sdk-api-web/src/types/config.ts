/**
 * @file Configuration Capability Type Definitions
 * @description Defines core types for the configuration management system
 * @module @brix/runtime-sdk-api-web/types/config
 * @version 3.2.0
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file.
 *
 * [v3.1 Changes]
 * Added ConfigStoreCapability alias for Java/TS naming alignment.
 * ConfigCapability is now deprecated, use ConfigStoreCapability instead.
 *
 * 【v3.1 变更】
 * 添加 ConfigStoreCapability 别名以实现 Java/TS 命名对齐。
 * ConfigCapability 已弃用，请使用 ConfigStoreCapability。
 */

// =========================================
// Configuration Capability Type Identifier
// =========================================

/**
 * Configuration Capability Type Identifier
 */
export const ConfigCapabilityType = Symbol.for('ConfigCapability');

/**
 * Configuration Store Capability Type Identifier (Aligned with Java naming)
 * @since 3.1.0
 */
export const ConfigStoreCapabilityType = Symbol.for('ConfigStoreCapability');

// =========================================
// Configuration Capability Contract
// =========================================

/**
 * Configuration Store Capability Contract
 *
 * <p>Provides runtime configuration reading capability for plugins.</p>
 *
 * <p>This is the canonical name aligned with Java SDK (ConfigStoreCapability).</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const config = context.getCapability<ConfigStoreCapability>(ConfigStoreCapabilityType);
 * const apiBase = config.get<string>('api.baseUrl', '/api/v1');
 * const timeout = config.get<number>('http.timeout', 30000);
 * ```
 *
 * <h3>Configuration Sources</h3>
 * <ul>
 *   <li>Environment Variables</li>
 *   <li>Configuration Center</li>
 *   <li>Manifest Files</li>
 * </ul>
 *
 * @since 3.1.0
 */
export interface ConfigStoreCapability {
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

/**
 * Configuration Capability Contract
 *
 * @deprecated Use ConfigStoreCapability instead for Java/TS naming alignment.
 * Will be removed in v4.0.0.
 *
 * 【已弃用】请使用 ConfigStoreCapability 以实现 Java/TS 命名对齐。
 * 将在 v4.0.0 中移除。
 */
export interface ConfigCapability extends ConfigStoreCapability {}
