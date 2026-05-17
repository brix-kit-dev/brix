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
 * @file Event related type definitions
 * @description Define core types for the event system, including event messages, handlers, subscription options, etc.
 * @module @brix-sdk/runtime-sdk-api-mobile/types/event
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Maintains consistent event system type definitions with runtime-sdk-api-web.
 *
 * [Design Notes]
 * - Support both simple event bus and governed event bus modes
 * - Governed event bus provides complete event metadata and audit information
 */

// =========================================
// Event Message
// =========================================

/**
 * Event Message
 *
 * <p>Encapsulates complete event information, including type, payload, timestamp, and source.</p>
 */
export interface EventMessage<T = unknown> {
  /** Event type */
  readonly eventType: string;
  /** Event payload */
  readonly payload: T;
  /** Timestamp */
  readonly timestamp: number;
  /** Event source (plugin ID) */
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
   * Unsubscribe
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
 * <p>Event with complete metadata for observability and auditing.</p>
 */
export interface GovernedEvent<T = unknown> {
  /** Event type */
  readonly type: string;

  /** Event payload */
  readonly payload: T;

  /** Event metadata */
  readonly metadata: GovernedEventMetadata;
}

/**
 * Governed Event Metadata
 */
export interface GovernedEventMetadata {
  /** Event ID (unique identifier) */
  readonly eventId: string;

  /** Event source (plugin ID) */
  readonly source: string;

  /** Timestamp */
  readonly timestamp: number;

  /** Trace ID (for distributed tracing) */
  readonly traceId?: string;

  /** Correlation ID (for correlating related events) */
  readonly correlationId?: string;

  /** Version */
  readonly version?: string;

  /** Custom tags */
  readonly tags?: Record<string, string>;
}

/**
 * Governed Event Subscription Options
 */
export interface GovernedSubscriptionOptions extends SubscriptionOptions {
  /** Event type filter (supports wildcards) */
  readonly typePattern?: string;

  /** Source filter */
  readonly sourceFilter?: string | string[];
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
   * Reject new events when queue is full
   * 队列满时拒绝新事件
   */
  | 'reject'
  /**
   * Block the emit call until space is available
   * 阻塞 emit 调用直到有空间可用
   */
  | 'block';

/**
 * Backpressure Configuration
 *
 * <p>Configuration for event bus backpressure management.</p>
 *
 * @since 3.3.0
 */
export interface BackpressureConfig {
  /**
   * Maximum queue depth per event type
   * @default 1000
   */
  readonly maxQueueDepth?: number;

  /**
   * Global maximum queue depth
   * @default 10000
   */
  readonly globalMaxQueueDepth?: number;

  /**
   * Overflow strategy when queue is full
   * @default 'drop-oldest'
   */
  readonly overflowStrategy?: BackpressureOverflowStrategy;

  /**
   * Warning threshold percentage (0-100)
   * @default 80
   */
  readonly warningThreshold?: number;

  /**
   * Enable backpressure metrics collection
   * @default true
   */
  readonly enableMetrics?: boolean;
}

/**
 * Backpressure Metrics
 * @since 3.3.0
 */
export interface BackpressureMetrics {
  /** Current queue depth per event type */
  readonly queueDepthByType: ReadonlyMap<string, number>;
  /** Total events in queue */
  readonly totalQueueDepth: number;
  /** Number of events dropped */
  readonly droppedCount: number;
  /** Number of events rejected */
  readonly rejectedCount: number;
  /** Queue utilization percentage */
  readonly utilizationPercent: number;
}

/**
 * Governed Event Bus Capability Contract
 *
 * <p>Event bus with complete metadata and audit capabilities.</p>
 */
export interface GovernedEventBusCapability {
  /**
   * Publish event
   *
   * @param event Event object
   */
  publish<T>(event: GovernedEvent<T>): void;

  /**
   * Subscribe to event
   *
   * @param eventType Event type
   * @param handler Event handler
   * @param options Subscription options
   * @returns Unsubscribe function
   */
  subscribe<T>(
    eventType: string,
    handler: (event: GovernedEvent<T>) => void,
    options?: GovernedSubscriptionOptions
  ): () => void;

  /**
   * Get event history
   *
   * @param eventType Event type
   * @param limit Limit count
   * @returns Event history list
   */
  getHistory<T>(eventType: string, limit?: number): GovernedEvent<T>[];

  // =========================================
  // Backpressure Management (v3.3.0)
  // =========================================

  /**
   * Configure backpressure settings
   * @since 3.3.0
   */
  configureBackpressure?(config: BackpressureConfig): void;

  /**
   * Get current backpressure metrics
   * @since 3.3.0
   */
  getBackpressureMetrics?(): BackpressureMetrics;

  /**
   * Check if backpressure is active
   * @since 3.3.0
   */
  isBackpressureActive?(eventType?: string): boolean;
}
