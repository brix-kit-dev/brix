/**
 * @file index.ts
 * @description @brix/infra-adapter-http-web package entry point
 * @module @brix/infra-adapter-http-web
 * @version 3.1.0
 * 
 * Brix Platform HTTP Infrastructure Adapter provides HTTP library-agnostic capabilities:
 * - Retry mechanism: Exponential backoff + random jitter
 * - Cache capability: TTL-based in-memory caching
 * - Error handling: Unified HTTP error representation
 * 
 * 【架构说明】
 * 本包属于 v3.0 架构的基础设施适配层(Layer 2.5)，提供与具体 HTTP 库解耦的通用能力。
 * 不依赖任何具体 HTTP 库（如 axios），可以与任何 HTTP 实现配合使用。
 * 
 * Architecture Position:
 * This package belongs to the infrastructure adapter layer (Layer 2.5) of v3.0 architecture,
 * providing common capabilities decoupled from specific HTTP libraries. It does not depend on
 * any specific HTTP library (like axios) and can work with any HTTP implementation.
 * 
 * @example
 * ```typescript
 * import { 
 *   withRetry, 
 *   SimpleCache, 
 *   HttpError,
 *   calculateBackoffDelay 
 * } from '@brix/infra-adapter-http-web';
 * 
 * // Using retry
 * const result = await withRetry(
 *   () => fetch('/api/users'),
 *   { maxRetries: 3 }
 * );
 * 
 * // Using cache
 * const cache = new SimpleCache({ defaultTTL: 60000 });
 * cache.set('users', userData);
 * const users = cache.get('users');
 * ```
 * 
 * @license Apache-2.0
 */

// ============================================================
// Interface Definitions Export
// ============================================================

export {
  // HTTP method type
  type HttpMethod,
  // Request configuration
  type RequestConfig,
  // Interceptor interfaces
  type RequestInterceptor,
  type ResponseInterceptor,
  type InterceptorManager,
  // Error class
  HttpError,
  // Error code constants
  HttpErrorCode,
  type HttpErrorCodeType,
  // Retryable constants
  RETRYABLE_STATUS_CODES,
  RETRYABLE_NETWORK_ERRORS,
} from './interface';

// ============================================================
// Retry Logic Export
// ============================================================

export {
  // Retry options
  type RetryOptions,
  // Default options
  DEFAULT_RETRY_OPTIONS,
  // Utility functions
  calculateBackoffDelay,
  shouldRetry,
  delay,
  // Higher-order functions
  withRetry,
  createRetryable,
} from './retry';

// ============================================================
// Cache Logic Export
// ============================================================

export {
  // Cache options
  type CacheOptions,
  // Default options
  DEFAULT_CACHE_OPTIONS,
  // Cache class
  SimpleCache,
  // Utility functions
  generateCacheKey,
  withCache,
} from './cache';

// ============================================================
// HTTP Capability Implementation Export
// ============================================================

export {
  // Implementation class
  HttpCapabilityImpl,
  // Factory function
  createHttpCapability,
  // Options type
  type HttpCapabilityImplOptions,
  // Interceptor types
  type HttpRequestInterceptor,
  type HttpResponseInterceptor,
} from './HttpCapabilityImpl';
