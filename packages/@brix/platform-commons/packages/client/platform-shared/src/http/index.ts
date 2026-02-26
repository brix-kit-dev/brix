/**
 * @file index.ts
 * @description HTTP client utilities re-export
 * @module @brix/platform-shared/http
 * @version 3.1.0
 * 
 * 【架构说明】
 * 本模块从 @brix/infra-adapter-http-web 重新导出 HTTP 相关功能。
 * 根据 v3.0 架构设计，HTTP 运行时实现代码位于基础设施适配层(Layer 2.5)，
 * platform-shared 层只提供类型定义和常量的重新导出。
 * 
 * This module re-exports HTTP functionality from @brix/infra-adapter-http-web.
 * Following v3.0 architecture design, HTTP runtime implementation resides in
 * the infrastructure adapter layer (Layer 2.5). The platform-shared layer only
 * provides type definitions and constant re-exports.
 * 
 * @example
 * ```typescript
 * // Import from platform-shared (for backward compatibility)
 * import { withRetry, SimpleCache, HttpError } from '@brix/platform-shared/http';
 * 
 * // Or import directly from infra-adapter (recommended)
 * import { withRetry, SimpleCache, HttpError } from '@brix/infra-adapter-http-web';
 * ```
 * 
 * @license Apache-2.0
 */

// ============================================================
// Re-export all HTTP utilities from infra-adapter-http-web
// ============================================================

export {
  // Interface definitions
  type HttpMethod,
  type RequestConfig,
  type RequestInterceptor,
  type ResponseInterceptor,
  type InterceptorManager,
  HttpError,
  HttpErrorCode,
  type HttpErrorCodeType,
  RETRYABLE_STATUS_CODES,
  RETRYABLE_NETWORK_ERRORS,
  // Retry logic
  type RetryOptions,
  DEFAULT_RETRY_OPTIONS,
  calculateBackoffDelay,
  shouldRetry,
  delay,
  withRetry,
  createRetryable,
  // Cache logic
  type CacheOptions,
  DEFAULT_CACHE_OPTIONS,
  SimpleCache,
  generateCacheKey,
  withCache,
} from '@brix/infra-adapter-http-web';

