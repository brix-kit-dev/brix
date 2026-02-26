/**
 * @file HTTP Capability Implementation
 * @description Implements HttpCapability Interface with retry and cache support
 * @module @brix/infra-adapter-http-web/HttpCapabilityImpl
 * @version 3.1.0
 *
 * Architecture Overview:
 * HttpCapabilityImpl is the implementation of HttpCapability interface,
 * providing unified HTTP requests with interceptor chain, retry, and cache.
 *
 * Core Responsibilities:
 * 1. Implement HttpCapability contract methods (get/post/put/delete/patch)
 * 2. Manage request/response interceptor chain
 * 3. Provide retry mechanism with exponential backoff
 * 4. Support response caching
 * 5. Automatically inject auth tokens and tenant context
 *
 * Architectural Constraints:
 * - This is the ONLY place where fetch/XMLHttpRequest is called
 * - Plugins MUST NOT call fetch directly, use this capability instead
 * - Interceptors are processed in registration order
 *
 * 【架构要点】
 * - 这是唯一调用 fetch 的地方，插件禁止直接调用 fetch
 * - 拦截器链支持请求/响应拦截，用于注入认证、日志等
 * - 支持指数退避重试和响应缓存
 */

import type {
  HttpCapability,
  HttpRequestConfig,
  HttpResponse,
} from '@brix/runtime-sdk-api-web';
import { withRetry, type RetryOptions, DEFAULT_RETRY_OPTIONS } from './retry';
import { SimpleCache, generateCacheKey, type CacheOptions, DEFAULT_CACHE_OPTIONS } from './cache';
import { HttpError, HttpErrorCode } from './interface';

/**
 * Request Interceptor
 */
export interface HttpRequestInterceptor {
  /**
   * Intercept request before sending
   *
   * @param config - Request configuration
   * @returns Modified request configuration
   */
  onRequest(config: HttpRequestConfig): HttpRequestConfig | Promise<HttpRequestConfig>;

  /**
   * Handle request error
   *
   * @param error - Error object
   * @returns Rethrow error or return modified config for retry
   */
  onRequestError?(error: unknown): HttpRequestConfig | Promise<never>;
}

/**
 * Response Interceptor
 */
export interface HttpResponseInterceptor {
  /**
   * Intercept response after receiving
   *
   * @param response - Response object
   * @returns Modified response
   */
  onResponse<T>(response: HttpResponse<T>): HttpResponse<T> | Promise<HttpResponse<T>>;

  /**
   * Handle response error
   *
   * @param error - Error object
   * @returns Rethrow error or return modified response for recovery
   */
  onResponseError?(error: unknown): HttpResponse<unknown> | Promise<never>;
}

/**
 * HTTP Capability Implementation Options
 *
 * 【配置项说明】
 * - baseURL: API 基础路径
 * - defaultTimeout: 默认请求超时时间
 * - retry: 重试配置
 * - cache: 缓存配置
 * - defaultHeaders: 默认请求头
 */
export interface HttpCapabilityImplOptions {
  /**
   * Base URL for all requests
   * API 基础路径
   * @default ''
   */
  baseURL?: string;

  /**
   * Default request timeout in milliseconds
   * 默认请求超时时间（毫秒）
   * @default 30000
   */
  defaultTimeout?: number;

  /**
   * Retry configuration
   * 重试配置
   */
  retry?: Partial<RetryOptions>;

  /**
   * Cache configuration
   * 缓存配置
   */
  cache?: Partial<CacheOptions>;

  /**
   * Default headers for all requests
   * 默认请求头
   */
  defaultHeaders?: Record<string, string>;

  /**
   * Enable request/response logging
   * 启用请求/响应日志
   * @default false
   */
  enableLogging?: boolean;

  /**
   * Auth token provider
   * 认证令牌提供者
   */
  authTokenProvider?: () => string | null | Promise<string | null>;

  /**
   * Tenant ID provider
   * 租户 ID 提供者
   */
  tenantIdProvider?: () => string | null;
}

/**
 * HTTP Capability Implementation
 *
 * Implements HttpCapability interface with full support for:
 * - Request/Response interceptors
 * - Automatic retry with exponential backoff
 * - Response caching
 * - Auth token injection
 * - Tenant context propagation
 *
 * 【核心实现说明】
 * 1. 基于原生 fetch API 实现，不依赖第三方 HTTP 库
 * 2. 拦截器链按注册顺序执行，支持异步拦截器
 * 3. 重试使用指数退避算法，避免雪崩效应
 * 4. 缓存仅对 GET 请求生效，支持 TTL 过期
 *
 * Usage Example:
 * ```typescript
 * // Create HTTP capability
 * const httpCapability = new HttpCapabilityImpl({
 *   baseURL: '/api/v1',
 *   defaultTimeout: 30000,
 *   retry: { maxRetries: 3 },
 *   authTokenProvider: () => sessionStorage.getItem('token'),
 * });
 *
 * // Add interceptor
 * httpCapability.addRequestInterceptor({
 *   onRequest: (config) => {
 *     console.log('Request:', config.url);
 *     return config;
 *   },
 * });
 *
 * // Make requests
 * const users = await httpCapability.get<User[]>('/users');
 * const created = await httpCapability.post<User>('/users', { name: 'John' });
 * ```
 */
export class HttpCapabilityImpl implements HttpCapability {
  /**
   * Base URL
   */
  private readonly baseURL: string;

  /**
   * Default timeout
   */
  private readonly defaultTimeout: number;

  /**
   * Retry options
   */
  private readonly retryOptions: RetryOptions;

  /**
   * Response cache
   */
  private readonly cache: SimpleCache;

  /**
   * Default headers
   */
  private readonly defaultHeaders: Record<string, string>;

  /**
   * Request interceptors
   */
  private readonly requestInterceptors: HttpRequestInterceptor[] = [];

  /**
   * Response interceptors
   */
  private readonly responseInterceptors: HttpResponseInterceptor[] = [];

  /**
   * Enable logging flag
   */
  private readonly enableLogging: boolean;

  /**
   * Auth token provider
   */
  private readonly authTokenProvider?: () => string | null | Promise<string | null>;

  /**
   * Tenant ID provider
   */
  private readonly tenantIdProvider?: () => string | null;

  /**
   * Constructor
   *
   * @param options - Configuration options
   */
  constructor(options: HttpCapabilityImplOptions = {}) {
    this.baseURL = options.baseURL ?? '';
    this.defaultTimeout = options.defaultTimeout ?? 30000;
    this.retryOptions = { ...DEFAULT_RETRY_OPTIONS, ...options.retry };
    this.cache = new SimpleCache({ ...DEFAULT_CACHE_OPTIONS, ...options.cache });
    this.defaultHeaders = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...options.defaultHeaders,
    };
    this.enableLogging = options.enableLogging ?? false;
    this.authTokenProvider = options.authTokenProvider;
    this.tenantIdProvider = options.tenantIdProvider;
  }

  /**
   * Add request interceptor
   *
   * @param interceptor - Request interceptor
   * @returns Removal function
   */
  addRequestInterceptor(interceptor: HttpRequestInterceptor): () => void {
    this.requestInterceptors.push(interceptor);
    return () => {
      const index = this.requestInterceptors.indexOf(interceptor);
      if (index !== -1) {
        this.requestInterceptors.splice(index, 1);
      }
    };
  }

  /**
   * Add response interceptor
   *
   * @param interceptor - Response interceptor
   * @returns Removal function
   */
  addResponseInterceptor(interceptor: HttpResponseInterceptor): () => void {
    this.responseInterceptors.push(interceptor);
    return () => {
      const index = this.responseInterceptors.indexOf(interceptor);
      if (index !== -1) {
        this.responseInterceptors.splice(index, 1);
      }
    };
  }

  /**
   * Send generic request
   *
   * @param config - Request configuration
   * @returns HTTP response
   */
  async request<T = unknown>(config: HttpRequestConfig): Promise<HttpResponse<T>> {
    // Apply request interceptors
    let processedConfig = { ...config };
    for (const interceptor of this.requestInterceptors) {
      try {
        processedConfig = await interceptor.onRequest(processedConfig);
      } catch (error) {
        if (interceptor.onRequestError) {
          processedConfig = await interceptor.onRequestError(error);
        } else {
          throw error;
        }
      }
    }

    // Check cache for GET requests
    const method = processedConfig.method ?? 'GET';
    if (method === 'GET') {
      const cacheKey = generateCacheKey(
        this.buildUrl(processedConfig.url, processedConfig.params),
        processedConfig.params ?? {},
      );
      const cached = this.cache.get(cacheKey);
      if (cached) {
        if (this.enableLogging) {
          console.debug('[HttpCapabilityImpl] Cache hit:', processedConfig.url);
        }
        return cached as HttpResponse<T>;
      }
    }

    // Execute request with retry
    const executeRequest = async (): Promise<HttpResponse<T>> => {
      return this.executeRequest<T>(processedConfig);
    };

    let response: HttpResponse<T>;
    try {
      response = await withRetry(executeRequest, this.retryOptions);
    } catch (error) {
      // Apply response error interceptors
      for (const interceptor of this.responseInterceptors) {
        if (interceptor.onResponseError) {
          try {
            response = (await interceptor.onResponseError(error)) as HttpResponse<T>;
            break;
          } catch {
            // Continue to next interceptor
          }
        }
      }
      if (!response!) {
        throw error;
      }
    }

    // Apply response interceptors
    for (const interceptor of this.responseInterceptors) {
      response = (await interceptor.onResponse(response)) as HttpResponse<T>;
    }

    // Cache GET responses
    if (method === 'GET' && response.status >= 200 && response.status < 300) {
      const cacheKey = generateCacheKey(
        this.buildUrl(processedConfig.url, processedConfig.params),
        processedConfig.params ?? {},
      );
      this.cache.set(cacheKey, response);
    }

    return response;
  }

  /**
   * GET request
   *
   * @param url - Request URL
   * @param params - Query parameters or request options
   * @returns Response data
   */
  async get<T = unknown>(
    url: string,
    params?: Record<string, unknown>,
  ): Promise<T> {
    // Handle both params object and options object with headers/timeout
    let queryParams: Record<string, unknown> | undefined;
    let headers: Record<string, string> | undefined;
    let timeout: number | undefined;

    if (params && ('headers' in params || 'timeout' in params)) {
      // This is an options object
      headers = params.headers as Record<string, string> | undefined;
      timeout = params.timeout as number | undefined;
      // Remove non-query params
      const { headers: _, timeout: __, ...rest } = params;
      queryParams = Object.keys(rest).length > 0 ? rest : undefined;
    } else {
      queryParams = params;
    }

    const response = await this.request<T>({
      url,
      method: 'GET',
      params: queryParams,
      headers,
      timeout,
    });
    return response.data;
  }

  /**
   * POST request
   *
   * @param url - Request URL
   * @param data - Request body
   * @returns Response data
   */
  async post<T = unknown>(url: string, data?: unknown): Promise<T> {
    const response = await this.request<T>({
      url,
      method: 'POST',
      data,
    });
    return response.data;
  }

  /**
   * PUT request
   *
   * @param url - Request URL
   * @param data - Request body
   * @returns Response data
   */
  async put<T = unknown>(url: string, data?: unknown): Promise<T> {
    const response = await this.request<T>({
      url,
      method: 'PUT',
      data,
    });
    return response.data;
  }

  /**
   * DELETE request
   *
   * @param url - Request URL
   * @returns Response data
   */
  async delete<T = unknown>(url: string): Promise<T> {
    const response = await this.request<T>({
      url,
      method: 'DELETE',
    });
    return response.data;
  }

  /**
   * PATCH request
   *
   * @param url - Request URL
   * @param data - Request body
   * @returns Response data
   */
  async patch<T = unknown>(url: string, data?: unknown): Promise<T> {
    const response = await this.request<T>({
      url,
      method: 'PATCH',
      data,
    });
    return response.data;
  }

  /**
   * Clear response cache
   *
   * @param pattern - Optional pattern to match cache keys
   */
  clearCache(pattern?: string): void {
    if (pattern) {
      this.cache.clearByPattern(new RegExp(pattern));
    } else {
      this.cache.clear();
    }
  }

  /**
   * Execute HTTP request using fetch API
   *
   * 【fetch 调用封装】
   * 这是唯一直接调用 fetch 的地方，包括：
   * 1. 构建完整 URL
   * 2. 注入认证头和租户上下文
   * 3. 处理超时
   * 4. 统一错误处理
   *
   * @param config - Request configuration
   * @returns HTTP response
   */
  private async executeRequest<T>(config: HttpRequestConfig): Promise<HttpResponse<T>> {
    const url = this.buildUrl(config.url, config.params);
    const method = config.method ?? 'GET';
    const timeout = config.timeout ?? this.defaultTimeout;

    // Build headers with auth and tenant context
    const headers = await this.buildHeaders(config.headers);

    // Log request if enabled
    if (this.enableLogging) {
      console.debug('[HttpCapabilityImpl] Request:', method, url);
    }

    // Create abort controller for timeout
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);

    try {
      const fetchOptions: RequestInit = {
        method,
        headers,
        signal: controller.signal,
      };

      // Add body for non-GET requests
      if (config.data !== undefined && method !== 'GET' && method !== 'HEAD') {
        fetchOptions.body = JSON.stringify(config.data);
      }

      const response = await fetch(url, fetchOptions);

      // Parse response
      const contentType = response.headers.get('Content-Type') ?? '';
      let data: T;

      if (contentType.includes('application/json')) {
        data = await response.json();
      } else if (contentType.includes('text/')) {
        data = (await response.text()) as unknown as T;
      } else {
        data = (await response.blob()) as unknown as T;
      }

      // Convert headers to object
      const responseHeaders: Record<string, string> = {};
      response.headers.forEach((value, key) => {
        responseHeaders[key] = value;
      });

      const httpResponse: HttpResponse<T> = {
        data,
        status: response.status,
        statusText: response.statusText,
        headers: responseHeaders,
      };

      // Log response if enabled
      if (this.enableLogging) {
        console.debug('[HttpCapabilityImpl] Response:', response.status, url);
      }

      // Throw error for non-2xx responses
      if (!response.ok) {
        throw new HttpError(
          `HTTP ${response.status}: ${response.statusText}`,
          HttpErrorCode.SERVER_ERROR,
          { status: response.status, response: data },
        );
      }

      return httpResponse;
    } catch (error) {
      // Handle abort/timeout
      if (error instanceof Error && error.name === 'AbortError') {
        throw new HttpError(
          `Request timeout after ${timeout}ms`,
          HttpErrorCode.TIMEOUT,
          { retryable: true },
        );
      }

      // Handle network errors
      if (error instanceof TypeError && error.message.includes('fetch')) {
        throw new HttpError(
          'Network error: Unable to connect',
          HttpErrorCode.NETWORK_ERROR,
          { retryable: true },
        );
      }

      // Re-throw HttpError as-is
      if (error instanceof HttpError) {
        throw error;
      }

      // Wrap other errors
      throw new HttpError(
        error instanceof Error ? error.message : 'Unknown error',
        HttpErrorCode.UNKNOWN,
      );
    } finally {
      clearTimeout(timeoutId);
    }
  }

  /**
   * Build full URL with query parameters
   *
   * @param url - Base URL or path
   * @param params - Query parameters
   * @returns Full URL
   */
  private buildUrl(url: string, params?: Record<string, unknown>): string {
    // Determine base URL
    let fullUrl: string;
    if (url.startsWith('http://') || url.startsWith('https://')) {
      fullUrl = url;
    } else {
      fullUrl = `${this.baseURL}${url.startsWith('/') ? url : `/${url}`}`;
    }

    // Add query parameters
    if (params && Object.keys(params).length > 0) {
      const searchParams = new URLSearchParams();
      for (const [key, value] of Object.entries(params)) {
        if (value !== undefined && value !== null) {
          searchParams.append(key, String(value));
        }
      }
      const queryString = searchParams.toString();
      if (queryString) {
        fullUrl += fullUrl.includes('?') ? `&${queryString}` : `?${queryString}`;
      }
    }

    return fullUrl;
  }

  /**
   * Build request headers with auth and tenant context
   *
   * 【请求头构建】
   * 1. 合并默认头和自定义头
   * 2. 注入 Authorization 头（如果有 token provider）
   * 3. 注入 X-Tenant-ID 头（如果有 tenant provider）
   *
   * @param customHeaders - Custom headers
   * @returns Merged headers
   */
  private async buildHeaders(
    customHeaders?: Record<string, string>,
  ): Promise<Record<string, string>> {
    const headers: Record<string, string> = {
      ...this.defaultHeaders,
      ...customHeaders,
    };

    // Inject auth token
    if (this.authTokenProvider) {
      const token = await this.authTokenProvider();
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }
    }

    // Inject tenant ID
    if (this.tenantIdProvider) {
      const tenantId = this.tenantIdProvider();
      if (tenantId) {
        headers['X-Tenant-ID'] = tenantId;
      }
    }

    return headers;
  }
}

/**
 * Factory Function: Create HTTP Capability
 *
 * 【工厂函数说明】
 * 提供便捷的 HTTP 能力创建方式
 *
 * @param options - Configuration options
 * @returns HttpCapabilityImpl instance
 */
export function createHttpCapability(
  options?: HttpCapabilityImplOptions,
): HttpCapabilityImpl {
  return new HttpCapabilityImpl(options);
}
