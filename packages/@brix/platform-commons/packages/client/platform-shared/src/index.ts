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
 * @file platform-shared module entry
 * @description Platform common layer shared module - Cross-platform types, constants and utility functions
 * @module @brix/platform-shared
 * @version 3.0.0
 * 
 * Module Description:
 * platform-shared is the shared module of the platform common layer.
 * Provides types, constants and utility functions shared between Web and Mobile platforms.
 * 
 * Architecture Position:
 * ```text
 * +----------------------------------------------------------------------------+
 * | Capability Implementation Layer (platform-commons)                        |
 * +----------------------------------------------------------------------------+
 * | Web Modules                                                                |
 * | +-- platform-router-web                                                    |
 * | +-- platform-navigation-web                                                |
 * | +-- platform-state-web                                                     |
 * | +-- platform-eventbus-web                                                  |
 * | +-- platform-auth-web                                                      |
 * | +-- platform-layout-web                                                    |
 * | +-- platform-theme-web                                                     |
 * | +-- platform-i18n-web                                                      |
 * +----------------------------------------------------------------------------+
 * | Shared Module (this module)                                                |
 * | +-- platform-shared                                                        |
 * |     +-- types/ (type definitions)                                          |
 * |     +-- constants/ (constants)                                             |
 * |     +-- utils/ (utility functions)                                         |
 * +----------------------------------------------------------------------------+
 * ```
 */

// ============================================================================
// Types
// ============================================================================

export type {
  PlatformType,
  RuntimeEnvironment,
  LogLevel,
  PlatformConfig,
  PluginMetadata,
  PlatformError,
  AsyncResult,
  PaginationParams,
  PaginatedResult,
  BaseEvent,
  EventMetadata,
  MetadataEvent,
  EventPriority,
  EventSubscriptionOptions,
  EventPublishOptions,
} from './types';

// ============================================================================
// Constants
// ============================================================================

export {
  PLATFORM_VERSION,
  CAPABILITY_PREFIX,
  DEFAULT_TIMEOUT,
  DEFAULT_RETRY_COUNT,
  DEFAULT_RETRY_DELAY,
  MAX_EVENT_HISTORY,
  STORAGE_KEY_PREFIX,
  STORAGE_KEYS,
  HTTP_METHODS,
  HTTP_STATUS,
  COMMON_ERROR_CODES,
  AUTH_ERROR_CODES,
  NAVIGATION_ERROR_CODES,
  PLUGIN_ERROR_CODES,
  ERROR_CODES,
} from './constants';

// ============================================================================
// Utility Functions
// ============================================================================

export {
  debounce,
  throttle,
  generateUUID,
  isNullOrUndefined,
  isNotNullOrUndefined,
  isString,
  isNumber,
  isBoolean,
  isFunction,
  isObject,
  isArray,
  isPromise,
  assertNotNull,
  assert,
  deepClone,
  deepMerge,
  // ID generation utilities
  generateShortId,
  generateNanoId,
  generateTimestampId,
  extractTimestampFromId,
  SimpleSnowflake,
  generateSnowflakeId,
  createSequenceIdGenerator,
  createDailySequenceIdGenerator,
  isValidUUID,
  // Format utilities
  formatDate,
  formatRelativeTime,
  formatCurrency,
  formatNumber,
  formatPercent,
  formatFileSize,
  parseFileSize,
  formatDuration,
  capitalize,
  truncate,
  kebabCase,
  camelCase,
  // Timing utilities
  delay,
  delayWith,
  cancelableDelay,
  withTimeout,
  nextTick,
  requestIdleCallback,
  // Validator utilities
  isValidEmail,
  isValidPhone,
  isValidUrl,
  isValidIPv4,
  isValidJSON,
  isValidIdCard,
  isValidUnifiedCreditCode,
  isValidBankCard,
  checkPasswordStrength,
  isEmpty,
  isNumeric,
  isInteger,
  isPositive,
  isInRange,
  type PasswordStrength,
  type PasswordStrengthResult,
} from './utils';

// ============================================================================
// HTTP Utilities (merged from @brix/http-client)
// ============================================================================

export {
  // 接口定义
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
  // 重试逻辑
  type RetryOptions,
  DEFAULT_RETRY_OPTIONS,
  calculateBackoffDelay,
  shouldRetry,
  delay as httpDelay,
  withRetry,
  createRetryable,
  // 缓存逻辑
  type CacheOptions,
  DEFAULT_CACHE_OPTIONS,
  SimpleCache,
  generateCacheKey,
  withCache,
} from './http';
