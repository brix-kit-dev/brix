/**
 * @file Event type definitions
 * @description Cross-platform shared event type definitions
 * @module @brix/platform-shared/types/events
 * @version 3.1.0
 * 
 * 【架构说明】
 * v3.1.0 重构：EventMetadata 现在从 @brix/runtime-sdk-api-web 重新导出，
 * 消除类型并行定义问题。runtime-sdk-api-web 是权威定义位置。
 * 
 * Architecture Note:
 * v3.1.0 refactoring: EventMetadata is now re-exported from @brix/runtime-sdk-api-web,
 * eliminating parallel type definition issues. runtime-sdk-api-web is the authoritative source.
 */

// ============================================================
// Re-export EventMetadata from runtime-sdk-api-web
// ============================================================

/**
 * Re-export EventMetadata from the authoritative source.
 * 
 * 从权威定义位置重新导出 EventMetadata 类型。
 * @see {@link https://github.com/brix-framework/runtime-sdk | @brix/runtime-sdk-api-web}
 */
export { type EventMetadata } from '@brix/runtime-sdk-api-web';

// ============================================================
// Platform-specific Event Types (unique to platform-shared)
// ============================================================

/**
 * Base event interface.
 * 
 * 基础事件接口，定义所有事件的公共字段。
 */
export interface BaseEvent {
  /**
   * Event type
   */
  type: string;
  
  /**
   * Event timestamp
   */
  timestamp: number;
  
  /**
   * Event source
   */
  source?: string;
}

/**
 * Event with metadata.
 * 
 * 带元数据的事件接口。
 */
export interface MetadataEvent extends BaseEvent {
  /**
   * Event metadata (from runtime-sdk-api-web)
   */
  metadata: import('@brix/runtime-sdk-api-web').EventMetadata;
}

/**
 * Event priority levels.
 */
export type EventPriority = 'low' | 'normal' | 'high' | 'critical';

/**
 * Event subscription options.
 */
export interface EventSubscriptionOptions {
  /**
   * Whether to trigger only once
   */
  once?: boolean;
  
  /**
   * Priority
   */
  priority?: EventPriority;
  
  /**
   * Filter function
   */
  filter?: (event: unknown) => boolean;
}

/**
 * Event publish options.
 */
export interface EventPublishOptions {
  /**
   * Whether async
   */
  async?: boolean;
  
  /**
   * Delay in milliseconds
   */
  delay?: number;
  
  /**
   * Debounce in milliseconds
   */
  debounce?: number;
  
  /**
   * Throttle in milliseconds
   */
  throttle?: number;
}
