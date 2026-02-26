/**
 * @file Platform common type definitions
 * @description Cross-platform shared type definitions
 * @module @brix/platform-shared/types/platform
 * @version 3.1.0
 * 
 * 【架构说明】
 * v3.1.0 重构：PluginMetadata 现在从 @brix/runtime-sdk-api-web 重新导出，
 * 消除类型并行定义问题。runtime-sdk-api-web 是权威定义位置。
 * 
 * Architecture Note:
 * v3.1.0 refactoring: PluginMetadata is now re-exported from @brix/runtime-sdk-api-web,
 * eliminating parallel type definition issues. runtime-sdk-api-web is the authoritative source.
 */

// ============================================================
// Re-export PluginMetadata from runtime-sdk-api-web
// ============================================================

/**
 * Re-export PluginMetadata from the authoritative source.
 * 
 * 从权威定义位置重新导出 PluginMetadata 类型。
 * @see {@link https://github.com/brix-framework/runtime-sdk | @brix/runtime-sdk-api-web}
 */
export { type PluginMetadata } from '@brix/runtime-sdk-api-web';

// ============================================================
// Platform-specific Types (unique to platform-shared)
// ============================================================

/**
 * Platform type.
 */
export type PlatformType = 'web' | 'mobile' | 'desktop';

/**
 * Runtime environment.
 */
export type RuntimeEnvironment = 'development' | 'staging' | 'production';

/**
 * Log level.
 */
export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

/**
 * Platform configuration.
 */
export interface PlatformConfig {
  /**
   * Platform type
   */
  platform: PlatformType;
  
  /**
   * Runtime environment
   */
  environment: RuntimeEnvironment;
  
  /**
   * Log level
   */
  logLevel: LogLevel;
  
  /**
   * Whether debug mode is enabled
   */
  debug: boolean;
  
  /**
   * Version number
   */
  version: string;
  
  /**
   * Build time
   */
  buildTime?: string;
  
  /**
   * Git commit hash
   */
  commitHash?: string;
}

/**
 * Error information
 */
export interface PlatformError {
  /**
   * Error code
   */
  code: string;
  
  /**
   * Error message
   */
  message: string;
  
  /**
   * Detailed information
   */
  details?: unknown;
  
  /**
   * Stack trace
   */
  stack?: string;
  
  /**
   * Timestamp
   */
  timestamp: number;
  
  /**
   * Source module
   */
  source?: string;
}

/**
 * Async operation result
 */
export type AsyncResult<T, E = PlatformError> = 
  | { success: true; data: T }
  | { success: false; error: E };

/**
 * Pagination parameters
 */
export interface PaginationParams {
  /**
   * Current page number (starts from 1)
   */
  page: number;
  
  /**
   * Page size
   */
  pageSize: number;
  
  /**
   * Sort field
   */
  sortBy?: string;
  
  /**
   * Sort direction
   */
  sortOrder?: 'asc' | 'desc';
}

/**
 * Paginated result
 */
export interface PaginatedResult<T> {
  /**
   * Data list
   */
  items: T[];
  
  /**
   * Total count
   */
  total: number;
  
  /**
   * Current page number
   */
  page: number;
  
  /**
   * Page size
   */
  pageSize: number;
  
  /**
   * Total pages
   */
  totalPages: number;
  
  /**
   * Has next page
   */
  hasNext: boolean;
  
  /**
   * Has previous page
   */
  hasPrev: boolean;
}
