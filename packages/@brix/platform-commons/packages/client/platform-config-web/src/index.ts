/**
 * @file platform-config-web Module Entry
 * @description Web Configuration Capability Implementation - Implements ConfigCapability Interface
 * @module @brix/platform-config-web
 * @version 3.1.0
 *
 * Module Description:
 * platform-config-web is the implementation module for ConfigCapability interface.
 * Provides configuration loading, caching, and hot-reload capabilities.
 *
 * Architectural Position:
 * ```text
 * +-------------------------------------------------------------------------+
 * | Capability Contract Layer (runtime-sdk-api-web)                        |
 * | +-- ConfigCapability Interface Definition                              |
 * +-------------------------------------------------------------------------+
 * | Capability Implementation Layer (platform-commons)                     |
 * | +-- platform-config-web (this module) ⭐                               |
 * |      +-- ConfigCapabilityImpl (interface implementation)               |
 * |      +-- ConfigStore (in-memory configuration cache)                   |
 * |      +-- ConfigHttpClient (fetch configuration via HttpCapability)     |
 * +-------------------------------------------------------------------------+
 * ```
 *
 * Key Design:
 * 1. Configuration loaded from backend via HttpCapability (no direct fetch)
 * 2. Support for configuration hot-reload with polling
 * 3. Nested key access with dot notation (e.g., 'api.baseUrl')
 * 4. Change detection and notification
 *
 * Architectural Constraints:
 * ❌ Do not call fetch/axios directly, use HttpCapability
 * ❌ Do not store sensitive configuration in localStorage
 * ✅ All configuration changes must be logged
 *
 * 【模块职责】
 * - 实现 ConfigCapability 接口
 * - 通过 HttpCapability 从后端加载配置
 * - 提供配置缓存和热更新能力
 */

// ============================================================================
// Capability Implementation
// ============================================================================

export {
  ConfigCapabilityImpl,
  type ConfigCapabilityImplOptions,
  type ConfigChangeEvent,
  type ConfigChangeHandler,
} from './ConfigCapabilityImpl';

// ============================================================================
// Core Components
// ============================================================================

export { ConfigStore, type ConfigStoreOptions } from './ConfigStore';
export {
  ConfigHttpClient,
  ConfigFetchError,
  type ConfigHttpClientOptions,
  type ConfigResponse,
} from './ConfigHttpClient';

// ============================================================================
// Factory Function
// ============================================================================

import { ConfigCapabilityImpl, type ConfigCapabilityImplOptions } from './ConfigCapabilityImpl';

/**
 * Create Configuration Capability
 *
 * Factory function to create a new ConfigCapabilityImpl instance.
 *
 * 【工厂函数说明】
 * 提供便捷的配置能力创建方式，自动初始化
 *
 * Usage Example:
 * ```typescript
 * const configCapability = await createConfigCapability({
 *   httpCapability,
 *   configEndpoint: '/api/v1/config',
 *   refreshInterval: 60000,
 * });
 *
 * // Use configuration
 * const apiBase = configCapability.get<string>('api.baseUrl');
 * ```
 *
 * @param options - Configuration options
 * @returns Initialized ConfigCapabilityImpl instance
 */
export async function createConfigCapability(
  options: ConfigCapabilityImplOptions,
): Promise<ConfigCapabilityImpl> {
  const capability = new ConfigCapabilityImpl(options);
  await capability.initialize();
  return capability;
}

/**
 * Create Configuration Capability (sync version)
 *
 * Factory function to create a ConfigCapabilityImpl without initialization.
 * Call initialize() manually before use.
 *
 * @param options - Configuration options
 * @returns ConfigCapabilityImpl instance (not initialized)
 */
export function createConfigCapabilitySync(
  options: ConfigCapabilityImplOptions,
): ConfigCapabilityImpl {
  return new ConfigCapabilityImpl(options);
}
