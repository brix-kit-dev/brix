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
 * @file Event-Related Type Definitions
 * @description Defines core types for the event system, including event messages, handlers, subscription options, etc.
 * @module @brix/runtime-sdk-api-web/types/event
 * @version 3.3.0
 *
 * [v3.3 Changes - Phase 3 Contract Layer Cleanup]
 * - Migrated BaseEvent, MetadataEvent from platform-shared to runtime-sdk-api-web (LL3 compliance)
 * - Added EventPriority, EventSubscriptionOptions, EventPublishOptions contract types
 * - runtime-sdk-api-web is now the authoritative source for all event contract types
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file.
 *
 * [Design Principles]
 * - Supports both simple Event Bus and Governed Event Bus modes
 * - Governed Event Bus provides complete event metadata and audit information
 * - All event-related contract types are defined here (single source of truth)
 */

// =========================================
// Base Event Types (Contract Layer - Authoritative Source)
// =========================================

/**
 * Base Event Interface
 *
 * <p>Defines the common fields for all events in the Brix Platform.
 * This is the foundational contract type for the event system.</p>
 *
 * <h3>Design Rationale</h3>
 * <ul>
 *   <li>Provides minimal required fields for event identification</li>
 *   <li>Extensible through interface inheritance</li>
 *   <li>Platform-agnostic (works on Web/Mobile/Server)</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * interface UserCreatedEvent extends BaseEvent {
 *   type: 'user:created';
 *   userId: string;
 *   email: string;
 * }
 * ```
 *
 * @since 3.3.0 Migrated from platform-shared (LL3 compliance)
 */
export interface BaseEvent {
  /**
   * Event type identifier
   *
   * <p>Follows the convention: `{domain}:{action}` (e.g., 'user:created', 'booking:confirmed').</p>
   */
  readonly type: string;

  /**
   * Event timestamp in milliseconds (Unix epoch)
   *
   * <p>Represents when the event was created/emitted.</p>
   */
  readonly timestamp: number;

  /**
   * Event source identifier (typically plugin ID)
   *
   * <p>Identifies which plugin or system component emitted the event.</p>
   */
  readonly source?: string;
}

/**
 * Event with Metadata
 *
 * <p>Extends BaseEvent with rich metadata for observability, auditing, and governance.
 * Used in scenarios requiring event tracing, multi-tenancy support, or detailed logging.</p>
 *
 * <h3>Relationship with Other Event Types</h3>
 * <ul>
 *   <li>{@link BaseEvent}: Minimal event contract</li>
 *   <li>{@link MetadataEvent}: Extended with observability metadata (this interface)</li>
 *   <li>{@link GovernedEvent}: Full governance support with strict typing</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const event: MetadataEvent = {
 *   type: 'order:placed',
 *   timestamp: Date.now(),
 *   source: 'plugin-orders',
 *   metadata: {
 *     sourcePlugin: 'plugin-orders',
 *     scope: 'host',
 *     traceId: 'trace-123',
 *     timestamp: Date.now(),
 *     tenantId: 'tenant-abc'
 *   }
 * };
 * ```
 *
 * @since 3.3.0 Migrated from platform-shared (LL3 compliance)
 */
export interface MetadataEvent extends BaseEvent {
  /**
   * Event metadata for observability and governance
   *
   * <p>Contains tracing information, tenant context, and scope designation.</p>
   */
  readonly metadata: EventMetadata;
}

/**
 * Event Priority Levels
 *
 * <p>Defines the priority levels for event processing order.
 * Higher priority events are processed before lower priority ones.</p>
 *
 * <h3>Priority Semantics</h3>
 * <ul>
 *   <li>`low`: Background tasks, analytics, non-critical updates</li>
 *   <li>`normal`: Standard business events (default)</li>
 *   <li>`high`: Important business events requiring prompt processing</li>
 *   <li>`critical`: System-critical events, error handling, security alerts</li>
 * </ul>
 *
 * @since 3.3.0 Migrated from platform-shared (LL3 compliance)
 */
export type EventPriority = 'low' | 'normal' | 'high' | 'critical';

/**
 * Event Subscription Options (Extended)
 *
 * <p>Configuration options for event subscription with priority and filtering support.
 * Extends the basic {@link SubscriptionOptions} with additional capabilities.</p>
 *
 * <h3>Relationship with SubscriptionOptions</h3>
 * <ul>
 *   <li>{@link SubscriptionOptions}: Basic subscription options (once, filter)</li>
 *   <li>{@link EventSubscriptionOptions}: Extended options with priority support</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * eventBus.on('user:action', handler, {
 *   priority: 'high',
 *   once: false,
 *   filter: (event) => event.userId === currentUserId
 * });
 * ```
 *
 * @since 3.3.0 Migrated from platform-shared (LL3 compliance)
 */
export interface EventSubscriptionOptions {
  /**
   * Whether to trigger only once
   *
   * <p>If true, the handler is automatically unsubscribed after first invocation.</p>
   */
  readonly once?: boolean;

  /**
   * Subscription priority
   *
   * <p>Determines the order in which handlers are invoked.
   * Higher priority handlers are called before lower priority ones.</p>
   *
   * @default 'normal'
   */
  readonly priority?: EventPriority;

  /**
   * Filter function for selective event handling
   *
   * <p>If provided, the handler is only invoked when the filter returns true.</p>
   *
   * @param event The event payload
   * @returns Whether to invoke the handler
   */
  readonly filter?: (event: unknown) => boolean;
}

/**
 * Event Publish Options
 *
 * <p>Configuration options for event publishing behavior.
 * Supports async/sync modes, delayed emission, and rate limiting.</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * eventBus.emit('analytics:track', payload, {
 *   async: true,
 *   debounce: 300,
 *   tags: ['analytics', 'user-action']
 * });
 * ```
 *
 * @since 3.3.0 Migrated from platform-shared (LL3 compliance)
 */
export interface EventPublishOptions {
  /**
   * Whether to emit asynchronously
   *
   * <p>If true, the event is queued and emitted in the next microtask.
   * If false, handlers are invoked synchronously.</p>
   *
   * @default true
   */
  readonly async?: boolean;

  /**
   * Delay before emission in milliseconds
   *
   * <p>If specified, the event emission is delayed by this amount.</p>
   */
  readonly delay?: number;

  /**
   * Debounce interval in milliseconds
   *
   * <p>If specified, rapid emissions of the same event type are debounced.
   * Only the last emission within the interval is actually sent.</p>
   */
  readonly debounce?: number;

  /**
   * Throttle interval in milliseconds
   *
   * <p>If specified, emissions are throttled to at most one per interval.</p>
   */
  readonly throttle?: number;
}

// =========================================
// Event Message
// =========================================

/**
 * Event Message
 *
 * <p>Encapsulates complete event information including type, payload, timestamp, and source.</p>
 */
export interface EventMessage<T = unknown> {
  /** Event Type */
  readonly eventType: string;
  /** Event Payload */
  readonly payload: T;
  /** Timestamp */
  readonly timestamp: number;
  /** Event Source (Plugin ID) */
  readonly source?: string;
}

// =========================================
// Subscription Options
// =========================================

/**
 * Event Subscription Options
 */
export interface SubscriptionOptions {
  /** Whether to trigger only once */
  readonly once?: boolean;
  /** Filter function */
  readonly filter?: (payload: unknown) => boolean;
}

// =========================================
// Event Handler
// =========================================

/**
 * Event Handler
 *
 * @typeParam T - Event payload type
 */
export type EventHandler<T = unknown> = (payload: T) => void;

/**
 * Unsubscribe Function
 */
export type Unsubscribe = () => void;

// =========================================
// Event Bus Capability
// =========================================

/**
 * Event Bus Capability Type Identifier
 */
export const EventBusCapabilityType = Symbol.for('EventBusCapability');

/**
 * Event Bus Capability Contract
 *
 * <p>Provides cross-plugin communication capability for plugins.</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const eventBus = context.getCapability<EventBusCapability>(EventBusCapabilityType);
 *
 * // Emit event
 * eventBus.emit('booking:selected', { bookingId: '123' });
 *
 * // Subscribe to event
 * const unsubscribe = eventBus.on('booking:selected', (payload) => {
 *   console.log('Booking selected:', payload);
 * });
 * ```
 */
export interface EventBusCapability {
  /**
   * Emit event
   *
   * @param eventType Event type
   * @param payload Event payload
   */
  emit(eventType: string, payload: unknown): void;

  /**
   * Subscribe to event
   *
   * @param eventType Event type
   * @param handler Event handler
   * @returns Unsubscribe function
   */
  on(eventType: string, handler: EventHandler): () => void;

  /**
   * Unsubscribe from event
   *
   * @param eventType Event type
   * @param handler Event handler
   */
  off(eventType: string, handler: EventHandler): void;
}

// =========================================
// Governed Event Bus Capability
// =========================================

/**
 * Governed Event Bus Capability Type Identifier
 */
export const GovernedEventBusCapabilityType = Symbol.for('GovernedEventBusCapability');

/**
 * Governed Event
 *
 * <p>Event containing complete metadata for observability and auditing.</p>
 */
export interface GovernedEvent<T = unknown> {
  /** Event Type */
  readonly type: string;

  /** Event Payload */
  readonly payload: T;

  /** Event Metadata */
  readonly metadata: GovernedEventMetadata;
}

/**
 * Governed Event Metadata
 */
export interface GovernedEventMetadata {
  /** Event ID (Unique Identifier) */
  readonly eventId: string;

  /** Emit Timestamp */
  readonly timestamp: number;

  /** Sender (Plugin ID) */
  readonly source: string;

  /** Tenant ID */
  readonly tenantId?: string;

  /**
   * Event Scope
   * - 'plugin': Visible only within the plugin
   * - 'host': Globally visible
   */
  readonly scope: 'plugin' | 'host';
}

// =========================================
// Event Metadata (Extended Version for Observability)
// =========================================

/**
 * Event Metadata
 *
 * <p>Extended metadata for Web-side event tracking and observability.
 * Compared to GovernedEventMetadata, provides more flexible field naming for Web scenarios.</p>
 *
 * <h3>Relationship with GovernedEventMetadata</h3>
 * <ul>
 *   <li>GovernedEventMetadata: Used for Governed Event Bus</li>
 *   <li>EventMetadata: Used for general event emission scenarios</li>
 * </ul>
 *
 * @since 3.2.0
 */
export interface EventMetadata {
  /** Sender Plugin ID */
  readonly sourcePlugin: string;

  /**
   * Event Scope
   * - 'plugin': Visible only within the plugin
   * - 'host': Globally visible (cross-plugin)
   */
  readonly scope: 'plugin' | 'host';

  /** Trace ID (for distributed tracing) */
  readonly traceId: string;

  /** Emit Timestamp (milliseconds) */
  readonly timestamp: number;

  /** Tenant ID (multi-tenant scenario) */
  readonly tenantId?: string;

  /** Custom Tags (for filtering and classification) */
  readonly tags?: ReadonlyArray<string>;
}

// =========================================
// Web Event Emit Options
// =========================================

/**
 * Web Event Emit Options
 *
 * <p>Configuration parameters controlling event emission behavior.</p>
 *
 * @since 3.2.0
 */
export interface WebEventEmitOptions {
  /**
   * Event Scope
   * - 'plugin': Visible only within the current plugin
   * - 'host': Globally visible, can be subscribed across plugins
   * @default 'host'
   */
  readonly scope?: 'plugin' | 'host';

  /**
   * Whether to emit synchronously
   *
   * <p>Default is asynchronous (via queueMicrotask),
   * set to true to immediately invoke all subscribers synchronously.</p>
   *
   * @default false
   */
  readonly sync?: boolean;

  /**
   * Custom Tags
   *
   * <p>Used for event classification and filtering, visible in logs and monitoring.</p>
   */
  readonly tags?: ReadonlyArray<string>;
}

// =========================================
// Backpressure Configuration
// =========================================

/**
 * Backpressure Overflow Strategy
 *
 * <p>Defines behavior when event queue reaches maxQueueDepth.</p>
 *
 * @since 3.3.0
 */
export type BackpressureOverflowStrategy =
  /**
   * Drop oldest events to make room for new ones
   * 丢弃最旧的事件以为新事件腾出空间
   */
  | 'drop-oldest'
  /**
   * Reject new events when queue is full (throws BackpressureError)
   * 队列满时拒绝新事件（抛出 BackpressureError）
   */
  | 'reject'
  /**
   * Block the emit call until space is available (async only)
   * 阻塞 emit 调用直到有空间可用（仅异步）
   */
  | 'block';

/**
 * Backpressure Configuration
 *
 * <p>Configuration for event bus backpressure management.
 * Prevents memory exhaustion and ensures system stability under high load.</p>
 *
 * 事件总线背压管理配置。
 * 防止内存耗尽，确保高负载下的系统稳定性。
 *
 * @since 3.3.0
 */
export interface BackpressureConfig {
  /**
   * Maximum queue depth per event type
   *
   * <p>When the number of pending events exceeds this limit,
   * the overflow strategy is applied.</p>
   *
   * 每个事件类型的最大队列深度。
   * 当待处理事件数量超过此限制时，将应用溢出策略。
   *
   * @default 1000
   */
  readonly maxQueueDepth?: number;

  /**
   * Global maximum queue depth (across all event types)
   *
   * 全局最大队列深度（跨所有事件类型）
   *
   * @default 10000
   */
  readonly globalMaxQueueDepth?: number;

  /**
   * Overflow strategy when queue is full
   *
   * 队列满时的溢出策略
   *
   * @default 'drop-oldest'
   */
  readonly overflowStrategy?: BackpressureOverflowStrategy;

  /**
   * Warning threshold percentage (0-100)
   *
   * <p>Emits a warning event when queue reaches this percentage of maxQueueDepth.</p>
   *
   * 警告阈值百分比。当队列达到 maxQueueDepth 的此百分比时发出警告事件。
   *
   * @default 80
   */
  readonly warningThreshold?: number;

  /**
   * Enable backpressure metrics collection
   *
   * 启用背压指标收集
   *
   * @default true
   */
  readonly enableMetrics?: boolean;
}

/**
 * Backpressure Metrics
 *
 * <p>Runtime metrics for monitoring backpressure state.</p>
 *
 * @since 3.3.0
 */
export interface BackpressureMetrics {
  /** Current queue depth per event type */
  readonly queueDepthByType: ReadonlyMap<string, number>;

  /** Total events in queue */
  readonly totalQueueDepth: number;

  /** Number of events dropped due to backpressure */
  readonly droppedCount: number;

  /** Number of events rejected due to backpressure */
  readonly rejectedCount: number;

  /** Number of times warning threshold was reached */
  readonly warningCount: number;

  /** Last warning timestamp */
  readonly lastWarningTimestamp?: number;

  /** Queue utilization percentage (0-100) */
  readonly utilizationPercent: number;
}

/**
 * Backpressure Error
 *
 * <p>Thrown when event is rejected due to backpressure.</p>
 *
 * @since 3.3.0
 */
export class BackpressureError extends Error {
  readonly eventType: string;
  readonly queueDepth: number;
  readonly maxQueueDepth: number;

  constructor(eventType: string, queueDepth: number, maxQueueDepth: number) {
    super(
      `Backpressure limit reached for event type '${eventType}': ` +
      `queue depth ${queueDepth} >= max ${maxQueueDepth}`
    );
    this.name = 'BackpressureError';
    this.eventType = eventType;
    this.queueDepth = queueDepth;
    this.maxQueueDepth = maxQueueDepth;
  }
}

// =========================================
// Web Event Subscribe Options
// =========================================

/**
 * Web Event Subscribe Options
 *
 * <p>Configuration parameters controlling event subscription behavior.</p>
 *
 * @since 3.2.0
 */
export interface WebEventSubscribeOptions {
  /**
   * Debounce Time (milliseconds)
   *
   * <p>For consecutive events triggered in a short time, only the last one is processed.</p>
   */
  readonly debounce?: number;

  /**
   * Throttle Time (milliseconds)
   *
   * <p>Limits the minimum time interval between event processing.</p>
   */
  readonly throttle?: number;

  /**
   * Event Filter
   *
   * <p>Handler is called only when this returns true.</p>
   */
  readonly filter?: (payload: unknown) => boolean;

  /**
   * Whether to trigger only once
   *
   * @default false
   */
  readonly once?: boolean;
}

/**
 * Governed Event Handler Function
 */
export type GovernedEventHandler<T = unknown> = (event: GovernedEvent<T>) => void;

/**
 * Governed Event Bus Capability Contract
 *
 * <p>Unlike the simple EventBusCapability, GovernedEventBusCapability provides:</p>
 * <ul>
 *   <li>Automatic injection of event metadata (eventId, timestamp, source)</li>
 *   <li>Event scope control (plugin/host)</li>
 *   <li>Complete event audit information</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const eventBus = context.getCapability<GovernedEventBusCapability>(GovernedEventBusCapabilityType);
 *
 * // Emit global event
 * eventBus.emit('booking:created', { bookingId: '123' }, 'host');
 *
 * // Subscribe to event (receive complete metadata)
 * eventBus.on('booking:created', (event) => {
 *   console.log(`Event ${event.metadata.eventId} from ${event.metadata.source}`);
 *   console.log('Payload:', event.payload);
 * });
 * ```
 */
export interface GovernedEventBusCapability {
  /**
   * Emit Event
   *
   * @param eventType Event type
   * @param payload Event payload
   * @param scope Event scope (default 'host')
   */
  emit<T = unknown>(eventType: string, payload: T, scope?: 'plugin' | 'host'): void;

  /**
   * Subscribe to Event
   *
   * @param eventType Event type
   * @param handler Event handler function, receives complete GovernedEvent
   * @returns Unsubscribe function
   */
  on<T = unknown>(eventType: string, handler: GovernedEventHandler<T>): Unsubscribe;

  /**
   * Subscribe to Event Once
   *
   * @param eventType Event type
   * @param handler Event handler function
   * @returns Unsubscribe function
   */
  once<T = unknown>(eventType: string, handler: GovernedEventHandler<T>): Unsubscribe;

  /**
   * Unsubscribe from Event
   *
   * @param eventType Event type
   * @param handler Event handler function
   */
  off<T = unknown>(eventType: string, handler: GovernedEventHandler<T>): void;

  // =========================================
  // Backpressure Management (v3.3.0)
  // =========================================

  /**
   * Configure backpressure settings
   *
   * 配置背压设置
   *
   * @param config Backpressure configuration
   * @since 3.3.0
   */
  configureBackpressure?(config: BackpressureConfig): void;

  /**
   * Get current backpressure metrics
   *
   * 获取当前背压指标
   *
   * @returns Current backpressure metrics
   * @since 3.3.0
   */
  getBackpressureMetrics?(): BackpressureMetrics;

  /**
   * Check if backpressure is active for an event type
   *
   * 检查某事件类型是否处于背压状态
   *
   * @param eventType Event type to check
   * @returns True if backpressure is active
   * @since 3.3.0
   */
  isBackpressureActive?(eventType?: string): boolean;

  /**
   * Reset backpressure metrics
   *
   * 重置背压指标
   *
   * @since 3.3.0
   */
  resetBackpressureMetrics?(): void;
}
