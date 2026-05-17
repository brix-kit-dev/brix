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
 * @file HTTP client capability type definitions
 * @description Define HTTP client capability contract, replacing direct use of fetch/axios
 * @module @brix-sdk/runtime-sdk-api-mobile/types/http
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Maintains consistent HTTP capability type definitions with runtime-sdk-api-web.
 *
 * [Architecture Notes - Red Line R3]
 * Plugin layer is prohibited from directly using axios / fetch and other HTTP client libraries,
 * must send requests through HttpCapability.
 */

// =========================================
// HTTP Capability Type Identifier
// =========================================

/**
 * HTTP Client Capability Type Identifier
 */
export const HttpCapabilityType = Symbol.for('HttpCapability');

// =========================================
// HTTP Request Configuration
// =========================================

/**
 * HTTP Request Configuration Options
 */
export interface HttpRequestConfig {
  /** Request URL (relative to baseURL or absolute path) */
  readonly url: string;
  /** HTTP method */
  readonly method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'HEAD' | 'OPTIONS';
  /** Request headers */
  readonly headers?: Record<string, string>;
  /** URL query parameters */
  readonly params?: Record<string, unknown>;
  /** Request body */
  readonly data?: unknown;
  /** Timeout (milliseconds) */
  readonly timeout?: number;
  /** Base URL */
  readonly baseURL?: string;
}

// =========================================
// HTTP Response Structure
// =========================================

/**
 * HTTP Response Structure
 */
export interface HttpResponse<T = unknown> {
  /** Response data */
  readonly data: T;
  /** HTTP status code */
  readonly status: number;
  /** Status text */
  readonly statusText: string;
  /** Response headers */
  readonly headers: Record<string, string>;
}

// =========================================
// HTTP Capability Contract
// =========================================

/**
 * HTTP Client Capability Contract
 *
 * <p>Provides unified HTTP request abstraction for the plugin layer, replacing direct axios / fetch calls.</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const http = runtimeContext.getCapability<HttpCapability>(HttpCapabilityType);
 * const products = await http.get<Product[]>('/api/v1/products');
 * const created = await http.post<Product>('/api/v1/products', newProduct);
 * ```
 *
 * <h3>Architecture Notes</h3>
 * <ul>
 *   <li>Shell layer provides implementation (can be based on fetch / axios, transparent to plugins)</li>
 *   <li>Automatically injects auth Token, tenant ID and other context headers</li>
 *   <li>Unified error handling and retry strategy</li>
 * </ul>
 */
export interface HttpCapability {
  /**
   * Send generic request
   *
   * @param config Request configuration
   * @returns Response result
   */
  request<T = unknown>(config: HttpRequestConfig): Promise<HttpResponse<T>>;

  /**
   * GET request
   *
   * @param url Request URL
   * @param params Query parameters
   * @returns Response data
   */
  get<T = unknown>(url: string, params?: Record<string, unknown>): Promise<T>;

  /**
   * POST request
   *
   * @param url Request URL
   * @param data Request body
   * @returns Response data
   */
  post<T = unknown>(url: string, data?: unknown): Promise<T>;

  /**
   * PUT request
   *
   * @param url Request URL
   * @param data Request body
   * @returns Response data
   */
  put<T = unknown>(url: string, data?: unknown): Promise<T>;

  /**
   * DELETE request
   *
   * @param url Request URL
   * @returns Response data
   */
  delete<T = unknown>(url: string): Promise<T>;

  /**
   * PATCH request
   *
   * @param url Request URL
   * @param data Request body
   * @returns Response data
   */
  patch<T = unknown>(url: string, data?: unknown): Promise<T>;
}
