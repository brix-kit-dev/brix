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
 * @file HTTP Capability Implementation
 * @description Implements HttpCapability Interface with retry and cache support
 * @module @brix-sdk/infra-adapter-http-web/HttpCapabilityImpl
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
 * ���ܹ�Ҫ�㡿
 * - ����Ψһ���� fetch �ĵط��������ֱֹ�ӵ��� fetch
 * - ��������֧������/��Ӧ���أ�����ע����֤����־��
 * - ֧��ָ���˱����Ժ���Ӧ����
 */

import type {
  HttpCapability,
  HttpRequestConfig,
  HttpResponse,
  EventBusCapability,
} from '@brix-sdk/runtime-sdk-api-web';
import { withRetry, type RetryOptions, DEFAULT_RETRY_OPTIONS } from './retry';
import { SimpleCache, generateCacheKey, type CacheOptions, DEFAULT_CACHE_OPTIONS } from './cache';
import { HttpError, HttpErrorCode, type RequestConfig } from './interface';
import { createErrorEventInterceptor } from './interceptors/error';

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
  onResponseError?(error: unknown): HttpResponse<unknown> | Promise<HttpResponse<unknown>>;
}

/**
 * HTTP Capability Implementation Options
 *
 * ��������˵����
 * - baseURL: API ����·��
 * - defaultTimeout: Ĭ������ʱʱ��
 * - retry: ��������
 * - cache: ��������
 * - defaultHeaders: Ĭ������ͷ
 */
export interface HttpCapabilityImplOptions {
  /**
   * Base URL for all requests
   * API ����·��
   * @default ''
   */
  baseURL?: string;

  /**
   * Default request timeout in milliseconds
   * Ĭ������ʱʱ�䣨���룩
   * @default 30000
   */
  defaultTimeout?: number;

  /**
   * Retry configuration
   * ��������
   */
  retry?: Partial<RetryOptions>;

  /**
   * Cache configuration
   * ��������
   */
  cache?: Partial<CacheOptions>;

  /**
   * Default headers for all requests
   * Ĭ������ͷ
   */
  defaultHeaders?: Record<string, string>;

  /**
   * Enable request/response logging
   * ��������/��Ӧ��־
   * @default false
   */
  enableLogging?: boolean;

  /**
   * Auth token provider
   * ��֤�����ṩ��
   */
  authTokenProvider?: () => string | null | Promise<string | null>;

  /**
   * Tenant ID provider
   * �⻧ ID �ṩ��
   */
  tenantIdProvider?: () => string | null;

  /**
   * Optional EventBus capability for emitting `system.http.error` events on
   * failed responses (Frontend Stability Reform Plan v1.0 — C-2).
   *
   * <p>When supplied, the constructor automatically registers a built-in
   * response interceptor that classifies errors and publishes structured
   * payloads to subscribers (e.g. `HttpErrorToaster`). Omit to opt out.</p>
   */
  eventBus?: EventBusCapability;
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
 * ������ʵ��˵����
 * 1. ����ԭ�� fetch API ʵ�֣������������� HTTP ��
 * 2. ����������ע��˳��ִ�У�֧���첽������
 * 3. ����ʹ��ָ���˱��㷨������ѩ��ЧӦ
 * 4. ������� GET ������Ч��֧�� TTL ����
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

    // Auto-register error-event interceptor when an EventBus is provided.
    // Layer 2C wiring: keeps subscribers (e.g. HttpErrorToaster) decoupled
    // from individual call sites. See C-2 in the stability reform plan.
    if (options.eventBus) {
      this.addResponseInterceptor(createErrorEventInterceptor(options.eventBus));
    }
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
   * ��fetch ���÷�װ��
   * ����Ψһֱ�ӵ��� fetch �ĵط���������
   * 1. �������� URL
   * 2. ע����֤ͷ���⻧������
   * 3. ������ʱ
   * 4. ͳһ������
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
      response.headers.forEach((value: string, key: string) => {
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
          { status: response.status, response: data, config: config as unknown as RequestConfig },
        );
      }

      return httpResponse;
    } catch (error) {
      // Handle abort/timeout
      if (error instanceof Error && error.name === 'AbortError') {
        throw new HttpError(
          `Request timeout after ${timeout}ms`,
          HttpErrorCode.TIMEOUT,
          { config: config as unknown as RequestConfig, retryable: true },
        );
      }

      // Handle network errors
      if (error instanceof TypeError && error.message.includes('fetch')) {
        throw new HttpError(
          'Network error: Unable to connect',
          HttpErrorCode.NETWORK_ERROR,
          { config: config as unknown as RequestConfig, retryable: true },
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
        { config: config as unknown as RequestConfig },
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
   * ������ͷ������
   * 1. �ϲ�Ĭ��ͷ���Զ���ͷ
   * 2. ע�� Authorization ͷ������� token provider��
   * 3. ע�� X-Tenant-ID ͷ������� tenant provider��
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
 * ����������˵����
 * �ṩ��ݵ� HTTP ����������ʽ
 *
 * @param options - Configuration options
 * @returns HttpCapabilityImpl instance
 */
export function createHttpCapability(
  options?: HttpCapabilityImplOptions,
): HttpCapabilityImpl {
  return new HttpCapabilityImpl(options);
}
