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
 * @file Configuration HTTP Client
 * @description Fetches configuration from backend via HttpCapability
 * @module @brix-sdk/platform-config-web/ConfigHttpClient
 * @version 3.1.0
 *
 * Architectural Constraints:
 * - Must not call fetch/axios directly; must go through HttpCapability
 * - Supports plugin-scoped configuration retrieval
 * - Auth headers are handled automatically (injected by HttpCapability)
 */

import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Configuration HTTP Client Options
 */
export interface ConfigHttpClientOptions {
  /**
   * HTTP Capability instance
   */
  httpCapability: HttpCapability;

  /**
   * Configuration API endpoint
   * @default '/api/v1/config'
   */
  endpoint?: string;

  /**
   * Plugin ID for scoped configuration
   */
  pluginId?: string;

  /**
   * Request timeout in milliseconds
   * @default 10000
   */
  timeout?: number;
}

/**
 * Configuration Response from Backend
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
 * Request Flow:
 * 1. Build request URL (with plugin ID parameter)
 * 2. Send GET request via HttpCapability
 * 3. Parse response and return configuration data
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
   * Request Parameters:
   * - pluginId: Plugin ID (optional, for fetching plugin-specific config)
   * - scope: Configuration scope (global/plugin)
   *
   * @returns Configuration data
   * @throws Error if fetch fails
   */
  async fetchConfig(): Promise<Record<string, unknown>> {
    try {
      // Build request URL with query parameters
      const url = this.buildUrl();

      // Fetch configuration via HttpCapability
      // HttpCapability automatically injects auth headers and handles errors
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
   * Version Check Optimization:
   * Used for efficient polling - only fetches full config when version changes
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
