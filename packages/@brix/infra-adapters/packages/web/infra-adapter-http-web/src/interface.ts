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
 * @file interface.ts
 * @description HTTP client core interfaces and types
 * @module @brix/infra-adapter-http-web
 * @author Brix Platform Team
 * @version 3.1.0
 * 
 * This module defines core HTTP client interfaces and types, including:
 * - Request configuration interface (RequestConfig)
 * - Interceptor interfaces (RequestInterceptor, ResponseInterceptor)
 * - HTTP error class (HttpError)
 * - Retryable status codes and network error constants
 * 
 * Design Principles:
 * - Decoupled from specific HTTP libraries, adaptable to fetch/axios implementations
 * - Complete TypeScript type definitions
 * - Supports interceptor chain processing
 * 
 * 【架构说明】
 * 本模块属于 v3.0 架构的基础设施适配层(Layer 2.5)，
 * 提供与具体 HTTP 库解耦的通用接口定义。
 */

// ============================================================
// Request Configuration
// ============================================================

/**
 * HTTP request methods.
 * 
 * Standard HTTP request method enumeration supporting RESTful API operations.
 */
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'HEAD' | 'OPTIONS';

/**
 * Request configuration interface.
 * 
 * Defines all configuration options required for making HTTP requests, decoupled from
 * specific implementations. This interface serves as a common abstraction that can be
 * adapted to any HTTP library (fetch, axios, etc.).
 * 
 * @example
 * ```typescript
 * const config: RequestConfig = {
 *   url: '/api/users',
 *   method: 'GET',
 *   timeout: 5000,
 *   headers: { 'X-Custom': 'value' }
 * };
 * ```
 */
export interface RequestConfig {
  /**
   * Request URL.
   * 
   * Can be a full URL or a relative path (relative to baseURL).
   */
  url?: string;

  /**
   * Request method.
   * @default 'GET'
   */
  method?: HttpMethod;

  /**
   * Base URL.
   * 
   * When url is a relative path, it will be concatenated with baseURL.
   */
  baseURL?: string;

  /**
   * Request headers.
   * 
   * Custom request headers that will be merged with default headers.
   */
  headers?: Record<string, string>;

  /**
   * URL query parameters.
   * 
   * Will be serialized and appended to the URL.
   */
  params?: Record<string, string | number | boolean | undefined>;

  /**
   * Request body data.
   * 
   * Request body for POST/PUT/PATCH requests.
   */
  data?: unknown;

  /**
   * Timeout duration (milliseconds).
   * @default 30000
   */
  timeout?: number;

  /**
   * Abort signal.
   * 
   * Used to cancel in-progress requests.
   */
  signal?: AbortSignal;

  /**
   * Whether authentication is required.
   * 
   * When true, interceptors will automatically add authentication headers.
   */
  auth?: boolean;

  /**
   * Number of retries.
   * @default 0
   */
  retries?: number;

  /**
   * Retry delay (milliseconds).
   * @default 1000
   */
  retryDelay?: number;

  /**
   * Whether to enable caching.
   * @default false
   */
  cache?: boolean;

  /**
   * Cache TTL (milliseconds).
   * @default 60000
   */
  cacheTTL?: number;

  /**
   * Response type.
   * 
   * Specifies how the response data should be parsed.
   */
  responseType?: 'json' | 'text' | 'blob' | 'arraybuffer';

  /**
   * Custom metadata.
   * 
   * Used to pass custom data between interceptors.
   */
  meta?: Record<string, unknown>;
}

// ============================================================
// Interceptor Interfaces
// ============================================================

/**
 * Request interceptor.
 * 
 * Processes configuration before the request is sent, commonly used for:
 * - Adding authentication headers
 * - Adding trace IDs
 * - Request parameter transformation
 */
export interface RequestInterceptor {
  /**
   * Pre-request processing.
   * 
   * @param config - Request configuration
   * @returns Processed configuration (can be a Promise)
   */
  onRequest?: (
    config: RequestConfig
  ) => RequestConfig | Promise<RequestConfig>;

  /**
   * Request error handling.
   * 
   * Called when an error occurs during request preparation.
   */
  onRequestError?: (error: Error) => void | Promise<void>;
}

/**
 * Response interceptor.
 * 
 * Processes data after the response is received, commonly used for:
 * - Unified error handling
 * - Data format transformation
 * - Token refresh
 * 
 * @typeParam T - Response data type
 */
export interface ResponseInterceptor<T = unknown> {
  /**
   * Response success processing.
   * 
   * @param response - Response data
   * @returns Processed response (can be a Promise)
   */
  onResponse?: (response: T) => T | Promise<T>;

  /**
   * Response error handling.
   * 
   * @param error - HTTP error
   * @returns Processed error (can be a Promise)
   */
  onResponseError?: (error: HttpError) => HttpError | Promise<HttpError>;
}

/**
 * Interceptor manager.
 * 
 * Manages the addition, removal, and execution of interceptors.
 * 
 * @typeParam T - Interceptor type
 */
export interface InterceptorManager<T> {
  /**
   * Add an interceptor.
   * 
   * @param interceptor - Interceptor instance
   * @returns Interceptor ID for later removal
   */
  use: (interceptor: T) => number;

  /**
   * Remove an interceptor.
   * 
   * @param id - Interceptor ID
   */
  eject: (id: number) => void;

  /**
   * Clear all interceptors.
   */
  clear: () => void;
}

// ============================================================
// HTTP Error Class
// ============================================================

/**
 * Unified HTTP error representation.
 * 
 * Contains error code, status code, request configuration, and other information
 * for error classification handling and retry decision-making.
 * 
 * 【关键设计】
 * HttpError 封装了所有 HTTP 相关错误信息，包括：
 * - code: 错误码（用于业务分类处理）
 * - status: HTTP 状态码
 * - retryable: 是否可重试（用于重试策略判断）
 * 
 * @example
 * ```typescript
 * try {
 *   await fetch('/api/data');
 * } catch (error) {
 *   if (error instanceof HttpError) {
 *     if (error.retryable) {
 *       // Can retry
 *     }
 *     console.log('Error code:', error.code);
 *     console.log('Status code:', error.status);
 *   }
 * }
 * ```
 */
export class HttpError extends Error {
  /**
   * Error code.
   * 
   * Used to distinguish different types of errors, such as NETWORK_ERROR, TIMEOUT, etc.
   */
  public readonly code: string;

  /**
   * HTTP status code.
   * 
   * HTTP status code returned by the server (e.g., 404, 500, etc.).
   */
  public readonly status?: number;

  /**
   * Request configuration.
   * 
   * Request configuration when the error occurred, useful for debugging.
   */
  public readonly config?: RequestConfig;

  /**
   * Response data.
   * 
   * Response body returned by the server (if any).
   */
  public readonly response?: unknown;

  /**
   * Whether the error is retryable.
   * 
   * Indicates whether this error is suitable for retry (e.g., network errors, timeouts).
   */
  public readonly retryable: boolean;

  /**
   * Creates an HttpError instance.
   * 
   * @param message - Error message
   * @param code - Error code
   * @param options - Additional options
   */
  constructor(
    message: string,
    code: string,
    options?: {
      status?: number;
      config?: RequestConfig;
      response?: unknown;
      retryable?: boolean;
    }
  ) {
    super(message);
    this.name = 'HttpError';
    this.code = code;
    this.status = options?.status;
    this.config = options?.config;
    this.response = options?.response;
    this.retryable = options?.retryable ?? false;
  }

  /**
   * Creates a network error.
   * 
   * Used for scenarios like network unreachable, connection reset, etc.
   * 
   * @param message - Error message
   * @param config - Request configuration
   * @returns HttpError instance
   */
  static networkError(message: string, config?: RequestConfig): HttpError {
    return new HttpError(message, 'NETWORK_ERROR', { config, retryable: true });
  }

  /**
   * Creates a timeout error.
   * 
   * Used for request timeout scenarios.
   * 
   * @param timeout - Timeout duration (milliseconds)
   * @param config - Request configuration
   * @returns HttpError instance
   */
  static timeoutError(timeout: number, config?: RequestConfig): HttpError {
    return new HttpError(
      `Request timed out after ${timeout}ms`,
      'TIMEOUT',
      { config, retryable: true }
    );
  }

  /**
   * Creates a canceled error.
   * 
   * Used for scenarios where the request was actively canceled.
   * 
   * @param config - Request configuration
   * @returns HttpError instance
   */
  static canceledError(config?: RequestConfig): HttpError {
    return new HttpError('Request canceled', 'CANCELED', { config, retryable: false });
  }

  /**
   * Determines if an error is a canceled error.
   * 
   * @param error - Error object
   * @returns Whether it is a canceled error
   */
  static isCanceled(error: unknown): boolean {
    return error instanceof HttpError && error.code === 'CANCELED';
  }
}

/**
 * HTTP error code constants.
 * 
 * Predefined error codes for error classification handling.
 */
export const HttpErrorCode = {
  /** Network error (cannot connect to server) */
  NETWORK_ERROR: 'NETWORK_ERROR',
  /** Request timeout */
  TIMEOUT: 'TIMEOUT',
  /** Request canceled */
  CANCELED: 'CANCELED',
  /** Unauthorized (401) */
  UNAUTHORIZED: 'UNAUTHORIZED',
  /** Forbidden (403) */
  FORBIDDEN: 'FORBIDDEN',
  /** Resource not found (404) */
  NOT_FOUND: 'NOT_FOUND',
  /** Server error (5xx) */
  SERVER_ERROR: 'SERVER_ERROR',
  /** Unknown error */
  UNKNOWN: 'UNKNOWN',
} as const;

/**
 * HTTP error code type.
 */
export type HttpErrorCodeType = (typeof HttpErrorCode)[keyof typeof HttpErrorCode];

// ============================================================
// Retryable Status Codes and Network Errors
// ============================================================

/**
 * Default retryable HTTP status codes.
 * 
 * These status codes typically indicate temporary failures suitable for retry:
 * - 408: Request Timeout
 * - 429: Too Many Requests (rate limiting)
 * - 500: Internal Server Error
 * - 502: Bad Gateway
 * - 503: Service Unavailable
 * - 504: Gateway Timeout
 */
export const RETRYABLE_STATUS_CODES = [
  408, // Request Timeout
  429, // Too Many Requests
  500, // Internal Server Error
  502, // Bad Gateway
  503, // Service Unavailable
  504, // Gateway Timeout
] as const;

/**
 * Default retryable network error types.
 * 
 * These errors typically indicate temporary network-level failures.
 */
export const RETRYABLE_NETWORK_ERRORS = [
  'ECONNRESET',    // Connection reset
  'ETIMEDOUT',     // Connection timeout
  'ECONNABORTED',  // Connection aborted
  'ENETUNREACH',   // Network unreachable
  'ENOTFOUND',     // DNS resolution failed
  'EPIPE',         // Broken pipe
] as const;
