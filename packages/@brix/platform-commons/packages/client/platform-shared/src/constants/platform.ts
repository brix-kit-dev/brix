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
 * @file Platform constants definitions
 * @description Cross-platform shared platform constants
 * @module @brix-sdk/platform-shared/constants/platform
 * @version 3.0.0
 */

/**
 * Platform version number
 */
export const PLATFORM_VERSION = '3.0.0';

/**
 * Capability type prefix
 */
export const CAPABILITY_PREFIX = 'brix';

/**
 * Default timeout in milliseconds
 */
export const DEFAULT_TIMEOUT = 30000;

/**
 * Default retry count
 */
export const DEFAULT_RETRY_COUNT = 3;

/**
 * Default retry delay in milliseconds
 */
export const DEFAULT_RETRY_DELAY = 1000;

/**
 * Maximum event history record count
 */
export const MAX_EVENT_HISTORY = 100;

/**
 * Storage key prefix
 */
export const STORAGE_KEY_PREFIX = 'brix:';

/**
 * Storage keys
 */
export const STORAGE_KEYS = {
  /**
   * Locale setting
   */
  LOCALE: `${STORAGE_KEY_PREFIX}locale`,
  
  /**
   * Theme setting
   */
  THEME: `${STORAGE_KEY_PREFIX}theme`,
  
  /**
   * User preferences
   */
  PREFERENCES: `${STORAGE_KEY_PREFIX}preferences`,
  
  /**
   * Authentication token
   */
  AUTH_TOKEN: `${STORAGE_KEY_PREFIX}auth:token`,
  
  /**
   * Refresh token
   */
  REFRESH_TOKEN: `${STORAGE_KEY_PREFIX}auth:refresh`,
} as const;

/**
 * HTTP methods
 */
export const HTTP_METHODS = {
  GET: 'GET',
  POST: 'POST',
  PUT: 'PUT',
  PATCH: 'PATCH',
  DELETE: 'DELETE',
  HEAD: 'HEAD',
  OPTIONS: 'OPTIONS',
} as const;

/**
 * HTTP status codes
 */
export const HTTP_STATUS = {
  OK: 200,
  CREATED: 201,
  NO_CONTENT: 204,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  CONFLICT: 409,
  UNPROCESSABLE_ENTITY: 422,
  TOO_MANY_REQUESTS: 429,
  INTERNAL_SERVER_ERROR: 500,
  BAD_GATEWAY: 502,
  SERVICE_UNAVAILABLE: 503,
} as const;
