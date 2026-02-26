/**
 * @file Error code definitions
 * @description Cross-platform shared error code constants
 * @module @brix/platform-shared/constants/errors
 * @version 3.0.0
 */

/**
 * Common error codes
 */
export const COMMON_ERROR_CODES = {
  /**
   * Unknown error
   */
  UNKNOWN: 'ERR_UNKNOWN',
  
  /**
   * Invalid parameters
   */
  INVALID_PARAMS: 'ERR_INVALID_PARAMS',
  
  /**
   * Operation timeout
   */
  TIMEOUT: 'ERR_TIMEOUT',
  
  /**
   * Network error
   */
  NETWORK: 'ERR_NETWORK',
  
  /**
   * Operation cancelled
   */
  CANCELLED: 'ERR_CANCELLED',
  
  /**
   * Resource not found
   */
  NOT_FOUND: 'ERR_NOT_FOUND',
  
  /**
   * Resource already exists
   */
  ALREADY_EXISTS: 'ERR_ALREADY_EXISTS',
} as const;

/**
 * Authentication error codes
 */
export const AUTH_ERROR_CODES = {
  /**
   * Not authenticated
   */
  UNAUTHENTICATED: 'ERR_AUTH_UNAUTHENTICATED',
  
  /**
   * Unauthorized
   */
  UNAUTHORIZED: 'ERR_AUTH_UNAUTHORIZED',
  
  /**
   * Invalid token
   */
  INVALID_TOKEN: 'ERR_AUTH_INVALID_TOKEN',
  
  /**
   * Token expired
   */
  TOKEN_EXPIRED: 'ERR_AUTH_TOKEN_EXPIRED',
  
  /**
   * Session expired
   */
  SESSION_EXPIRED: 'ERR_AUTH_SESSION_EXPIRED',
  
  /**
   * Refresh token failed
   */
  REFRESH_FAILED: 'ERR_AUTH_REFRESH_FAILED',
} as const;

/**
 * Navigation error codes
 */
export const NAVIGATION_ERROR_CODES = {
  /**
   * Page not found
   */
  PAGE_NOT_FOUND: 'ERR_NAV_PAGE_NOT_FOUND',
  
  /**
   * Navigation blocked
   */
  NAVIGATION_BLOCKED: 'ERR_NAV_BLOCKED',
  
  /**
   * Permission denied
   */
  PERMISSION_DENIED: 'ERR_NAV_PERMISSION_DENIED',
  
  /**
   * Invalid page ID
   */
  INVALID_PAGE_ID: 'ERR_NAV_INVALID_PAGE_ID',
} as const;

/**
 * Plugin error codes
 */
export const PLUGIN_ERROR_CODES = {
  /**
   * Plugin load failed
   */
  LOAD_FAILED: 'ERR_PLUGIN_LOAD_FAILED',
  
  /**
   * Plugin initialization failed
   */
  INIT_FAILED: 'ERR_PLUGIN_INIT_FAILED',
  
  /**
   * Plugin not found
   */
  NOT_FOUND: 'ERR_PLUGIN_NOT_FOUND',
  
  /**
   * Plugin version incompatible
   */
  VERSION_MISMATCH: 'ERR_PLUGIN_VERSION_MISMATCH',
  
  /**
   * Plugin dependency missing
   */
  MISSING_DEPENDENCY: 'ERR_PLUGIN_MISSING_DEPENDENCY',
} as const;

/**
 * All error codes
 */
export const ERROR_CODES = {
  ...COMMON_ERROR_CODES,
  ...AUTH_ERROR_CODES,
  ...NAVIGATION_ERROR_CODES,
  ...PLUGIN_ERROR_CODES,
} as const;
