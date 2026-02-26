/**
 * @file Configuration Capability Type Definitions
 * @description Defines core types for the configuration management system
 * @module @brix/runtime-sdk-api-web/types/config
 * @version 3.2.0
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file.
 */
/**
 * Configuration Capability Type Identifier
 */
export declare const ConfigCapabilityType: unique symbol;
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
 *   <li>Environment Variables</li>
 *   <li>Configuration Center</li>
 *   <li>Manifest Files</li>
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
//# sourceMappingURL=config.d.ts.map