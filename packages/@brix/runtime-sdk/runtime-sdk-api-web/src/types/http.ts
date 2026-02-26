/**
 * @file HTTP Client Capability Type Definitions
 * @description Defines HTTP client capability contract, replacing direct use of fetch/axios
 * @module @brix/runtime-sdk-api-web/types/http
 * @version 3.2.0
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file.
 *
 * [Architectural Constraint - R3]
 * Plugin layer is prohibited from using HTTP client libraries like axios/fetch directly,
 * requests must be made through the HttpCapability.
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
  /** HTTP Method */
  readonly method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'HEAD' | 'OPTIONS';
  /** Request Headers */
  readonly headers?: Record<string, string>;
  /** URL Query Parameters */
  readonly params?: Record<string, unknown>;
  /** Request Body */
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
  /** Response Data */
  readonly data: T;
  /** HTTP Status Code */
  readonly status: number;
  /** Status Text */
  readonly statusText: string;
  /** Response Headers */
  readonly headers: Record<string, string>;
}

// =========================================
// HTTP Capability Contract
// =========================================

/**
 * HTTP Client Capability Contract
 *
 * <p>Provides a unified HTTP request abstraction for the plugin layer, replacing direct axios/fetch calls.</p>
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
 *   <li>Shell layer provides the implementation (can be based on fetch/axios, transparent to plugins)</li>
 *   <li>Automatically injects auth tokens, tenant IDs, and other context headers</li>
 *   <li>Unified error handling and retry strategies</li>
 * </ul>
 */
export interface HttpCapability {
  /**
   * Send Generic Request
   *
   * @param config Request configuration
   * @returns Response result
   */
  request<T = unknown>(config: HttpRequestConfig): Promise<HttpResponse<T>>;

  /**
   * GET Request
   *
   * @param url Request URL
   * @param params Query parameters
   * @returns Response data
   */
  get<T = unknown>(url: string, params?: Record<string, unknown>): Promise<T>;

  /**
   * POST Request
   *
   * @param url Request URL
   * @param data Request body
   * @returns Response data
   */
  post<T = unknown>(url: string, data?: unknown): Promise<T>;

  /**
   * PUT Request
   *
   * @param url Request URL
   * @param data Request body
   * @returns Response data
   */
  put<T = unknown>(url: string, data?: unknown): Promise<T>;

  /**
   * DELETE Request
   *
   * @param url Request URL
   * @returns Response data
   */
  delete<T = unknown>(url: string): Promise<T>;

  /**
   * PATCH Request
   *
   * @param url Request URL
   * @param data Request body
   * @returns Response data
   */
  patch<T = unknown>(url: string, data?: unknown): Promise<T>;
}
