/**
 * @file Configuration HTTP Client
 * @description Fetches configuration from backend via HttpCapability
 * @module @brix/platform-config-web/ConfigHttpClient
 * @version 3.1.0
 *
 * 【架构约束】
 * - 不直接调用 fetch/axios，必须通过 HttpCapability
 * - 支持插件作用域的配置获取
 * - 自动处理认证头（由 HttpCapability 注入）
 */

import type { HttpCapability } from '@brix/runtime-sdk-api-web';

/**
 * Configuration HTTP Client Options
 */
export interface ConfigHttpClientOptions {
  /**
   * HTTP Capability instance
   * HTTP 能力实例
   */
  httpCapability: HttpCapability;

  /**
   * Configuration API endpoint
   * 配置 API 端点
   * @default '/api/v1/config'
   */
  endpoint?: string;

  /**
   * Plugin ID for scoped configuration
   * 插件 ID，用于获取作用域配置
   */
  pluginId?: string;

  /**
   * Request timeout in milliseconds
   * 请求超时时间（毫秒）
   * @default 10000
   */
  timeout?: number;
}

/**
 * Configuration Response from Backend
 * 后端配置响应结构
 */
export interface ConfigResponse {
  /**
   * Configuration data
   */
  data: Record<string, unknown>;

  /**
   * Configuration version (for caching)
   */
  version?: string;

  /**
   * Last modified timestamp
   */
  lastModified?: number;
}

/**
 * Configuration HTTP Client
 *
 * Fetches configuration from backend API using HttpCapability.
 *
 * 【请求流程】
 * 1. 构建请求 URL（包含插件 ID 参数）
 * 2. 通过 HttpCapability 发送 GET 请求
 * 3. 解析响应并返回配置数据
 *
 * Usage Example:
 * ```typescript
 * const client = new ConfigHttpClient({
 *   httpCapability,
 *   endpoint: '/api/v1/config',
 *   pluginId: 'booking',
 * });
 *
 * const config = await client.fetchConfig();
 * console.log(config); // { api: { baseUrl: '/api/v1' }, ... }
 * ```
 */
export class ConfigHttpClient {
  /**
   * HTTP Capability instance
   */
  private readonly httpCapability: HttpCapability;

  /**
   * Configuration endpoint
   */
  private readonly endpoint: string;

  /**
   * Plugin ID
   */
  private readonly pluginId?: string;

  /**
   * Request timeout
   */
  private readonly timeout: number;

  /**
   * Constructor
   *
   * @param options - Client options
   */
  constructor(options: ConfigHttpClientOptions) {
    this.httpCapability = options.httpCapability;
    this.endpoint = options.endpoint ?? '/api/v1/config';
    this.pluginId = options.pluginId;
    this.timeout = options.timeout ?? 10000;
  }

  /**
   * Fetch configuration from backend
   *
   * 【请求参数】
   * - pluginId: 插件 ID（可选，用于获取插件专属配置）
   * - scope: 配置作用域（global/plugin）
   *
   * @returns Configuration data
   * @throws Error if fetch fails
   */
  async fetchConfig(): Promise<Record<string, unknown>> {
    try {
      // Build request URL with query parameters
      const url = this.buildUrl();

      // Fetch configuration via HttpCapability
      // HttpCapability 会自动注入认证头和处理错误
      const response = await this.httpCapability.get<ConfigResponse>(url, {
        timeout: this.timeout,
        headers: {
          'Accept': 'application/json',
          'Cache-Control': 'no-cache',
        },
      });

      // Handle response
      if (response && typeof response === 'object') {
        // Check if response has data property (ConfigResponse format)
        if ('data' in response && response.data) {
          return response.data as Record<string, unknown>;
        }
        
        // Otherwise treat response itself as configuration
        return response as unknown as Record<string, unknown>;
      }

      return {};
    } catch (error) {
      console.error('[ConfigHttpClient] Failed to fetch configuration:', error);
      throw new ConfigFetchError(
        `Failed to fetch configuration from ${this.endpoint}`,
        error,
      );
    }
  }

  /**
   * Fetch plugin-specific configuration
   *
   * @param pluginId - Plugin ID
   * @returns Plugin configuration
   */
  async fetchPluginConfig(pluginId: string): Promise<Record<string, unknown>> {
    try {
      const url = `${this.endpoint}/plugins/${pluginId}`;

      const response = await this.httpCapability.get<ConfigResponse>(url, {
        timeout: this.timeout,
        headers: {
          'Accept': 'application/json',
        },
      });

      if (response && typeof response === 'object') {
        if ('data' in response && response.data) {
          return response.data as Record<string, unknown>;
        }
        return response as unknown as Record<string, unknown>;
      }

      return {};
    } catch (error) {
      console.error(
        `[ConfigHttpClient] Failed to fetch plugin configuration for ${pluginId}:`,
        error,
      );
      throw new ConfigFetchError(
        `Failed to fetch plugin configuration for ${pluginId}`,
        error,
      );
    }
  }

  /**
   * Check configuration version
   *
   * Used for efficient polling - only fetch full config if version changed.
   *
   * 【版本检查优化】
   * 用于高效轮询 - 仅在版本变化时获取完整配置
   *
   * @returns Current configuration version, or null if not available
   */
  async checkVersion(): Promise<string | null> {
    try {
      const url = `${this.endpoint}/version`;

      interface VersionResponse {
        version: string;
      }

      const response = await this.httpCapability.get<VersionResponse>(url, {
        timeout: 5000, // Shorter timeout for version check
        headers: {
          'Accept': 'application/json',
        },
      });

      return response?.version ?? null;
    } catch {
      // Version check failure is not critical
      return null;
    }
  }

  /**
   * Build request URL with query parameters
   *
   * @returns Full URL string
   */
  private buildUrl(): string {
    const params = new URLSearchParams();

    if (this.pluginId) {
      params.set('pluginId', this.pluginId);
    }

    params.set('scope', this.pluginId ? 'plugin' : 'global');

    const queryString = params.toString();
    return queryString ? `${this.endpoint}?${queryString}` : this.endpoint;
  }
}

/**
 * Configuration Fetch Error
 * 配置获取错误
 */
export class ConfigFetchError extends Error {
  /**
   * Original error cause
   */
  public readonly cause: unknown;

  /**
   * Constructor
   *
   * @param message - Error message
   * @param cause - Original error
   */
  constructor(message: string, cause?: unknown) {
    super(message);
    this.name = 'ConfigFetchError';
    this.cause = cause;

    // Maintain proper stack trace
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, ConfigFetchError);
    }
  }
}
