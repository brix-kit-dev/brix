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
 * @file Event Bus Capability Implementation
 * @description Implements GovernedEventBusCapability Interface
 * @module @brix/platform-eventbus-web/EventBusCapabilityImpl
 * @version 3.0.0
 * 
 * Architecture Overview:
 * EventBusCapabilityImpl is the implementation of GovernedEventBusCapability interface,
 * providing governable and traceable event communication capabilities.
 * 
 * Core Responsibilities:
 * 1. Automatically inject event metadata (sourcePlugin, traceId, timestamp)
 * 2. Route events based on scope
 * 3. Record event logs for debugging and tracing
 * 4. Support event waiting (waitFor)
 * 
 * Architectural Constraints:
 * - Do not use window.dispatchEvent
 * - Do not send events without metadata
 * - All events must be sent through GovernedEventBusCapability
 */

import type { 
  GovernedEventBusCapability, 
  GovernedEvent, 
  GovernedEventHandler, 
  GovernedEventMetadata,
  Unsubscribe,
  BackpressureConfig,
  BackpressureMetrics,
} from '@brix/runtime-sdk-api-web';
import { EventRouter } from './EventRouter';
import type { EventLogger } from './EventLogger';
import { BackpressureManager } from './BackpressureManager';

/**
 * Event Bus Capability Configuration
 */
export interface EventBusCapabilityConfig {
  /**
   * Event router instance
   */
  eventRouter: EventRouter;
  
  /**
   * Event logger instance
   */
  eventLogger: EventLogger;
  
  /**
   * Current plugin ID
   */
  pluginId: string;
  
  /**
   * Trace ID generator
   */
  traceIdGenerator?: () => string;
  
  /**
   * Tenant ID provider
   */
  tenantIdProvider?: () => string;

  /**
   * Backpressure configuration
   * 
   * 背压配置
   * 
   * @since 3.3.0
   */
  backpressure?: BackpressureConfig;

  /**
   * Warning callback for backpressure threshold
   * 
   * 背压阈值警告回调
   * 
   * @since 3.3.0
   */
  onBackpressureWarning?: (eventType: string, queueDepth: number, threshold: number) => void;
}

/**
 * Event Bus Capability Implementation
 * 
 * Implements GovernedEventBusCapability interface, providing governable event communication.
 * 
 * Usage Example:
 * ```typescript
 * // Create when Host initializes
 * const eventBusCapability = new EventBusCapabilityImpl({
 *   eventRouter: globalEventRouter,
 *   eventLogger: eventLogger,
 *   pluginId: 'booking',
 * });
 * 
 * // Plugin sends event
 * eventBusCapability.emit('booking:created', { id: '123' });
 * // Automatically adds metadata: { sourcePlugin: 'booking', scope: 'host', traceId: '...', timestamp: ... }
 * 
 * // Plugin subscribes to event
 * eventBusCapability.on('identity:login:success', (event) => {
 *   console.log('User logged in', event.payload);
 * });
 * ```
 */
export class EventBusCapabilityImpl implements GovernedEventBusCapability {
  /**
   * Event router
   */
  private eventRouter: EventRouter;
  
  /**
   * Event logger
   */
  private eventLogger: EventLogger;
  
  /**
   * Current plugin ID
   */
  private pluginId: string;
  
  /**
   * Trace ID generator
   */
  private traceIdGenerator: () => string;
  
  /**
   * Tenant ID provider
   */
  private tenantIdProvider: () => string;
  
  /**
   * Subscription unsubscribe function collection (for cleanup on destroy)
   */
  private subscriptions: Set<Unsubscribe> = new Set();

  /**
   * Backpressure manager
   * 背压管理器
   * @since 3.3.0
   */
  private backpressureManager: BackpressureManager;
  
  /**
   * Constructor
   * 
   * @param config - Configuration object
   */
  constructor(config: EventBusCapabilityConfig) {
    this.eventRouter = config.eventRouter;
    this.eventLogger = config.eventLogger;
    this.pluginId = config.pluginId;
    this.traceIdGenerator = config.traceIdGenerator ?? this.defaultTraceIdGenerator;
    this.tenantIdProvider = config.tenantIdProvider ?? (() => 'default');
    this.backpressureManager = new BackpressureManager(
      config.backpressure,
      config.onBackpressureWarning
    );
  }
  
  /**
   * Emit event
   * 
   * @typeParam T - Event payload type
   * @param eventType - Event type
   * @param payload - Event payload
   * @param scope - Event scope (default 'host')
   */
  emit<T = unknown>(eventType: string, payload: T, scope?: 'plugin' | 'host'): void {
    // Build event metadata
    const metadata: GovernedEventMetadata = {
      eventId: this.traceIdGenerator(),
      source: this.pluginId,
      scope: scope ?? 'host',
      timestamp: Date.now(),
      tenantId: this.tenantIdProvider(),
    };
    
    // Build complete event
    const event: GovernedEvent<T> = {
      type: eventType,
      payload: payload as T,
      metadata,
    };

    // Apply backpressure check
    // 应用背压检查
    const backpressureResult = this.backpressureManager.checkAndApply(
      eventType,
      event as GovernedEvent
    );

    // Log dropped events (use 'emit' action type since 'dropped' is not supported)
    // 记录被丢弃的事件（使用 'emit' action 类型，因为 'dropped' 不被支持）
    if (backpressureResult.droppedEvents.length > 0) {
      // Silently drop - backpressure manager already handles metrics
      // 静默丢弃 - 背压管理器已处理指标
    }

    // If event was not accepted (e.g., 'block' strategy), skip publishing
    // 如果事件未被接受（例如 'block' 策略），跳过发布
    if (!backpressureResult.accepted) {
      return;
    }
    
    // Log event
    const receiverCount = this.eventRouter.getSubscriberCount(eventType);
    this.eventLogger.log(event as GovernedEvent, 'emit', receiverCount);
    
    // Publish event asynchronously (next microtask)
    // 异步发布事件（下一个微任务）
    queueMicrotask(() => {
      this.eventRouter.publish(event as GovernedEvent);
      // Mark as processed after async publish
      this.backpressureManager.markProcessed(eventType);
    });
  }
  
  /**
   * Subscribe to event
   * 
   * @typeParam T - Event payload type
   * @param eventType - Event type
   * @param handler - Event handler function
   * @returns Unsubscribe function
   */
  on<T = unknown>(
    eventType: string,
    handler: GovernedEventHandler<T>
  ): Unsubscribe {
    // Wrap handler to log events
    const loggingHandler: GovernedEventHandler<T> = (event) => {
      this.eventLogger.log(event as GovernedEvent, 'receive');
      handler(event);
    };
    
    const unsubscribe = this.eventRouter.subscribe(
      eventType,
      loggingHandler as GovernedEventHandler,
      this.pluginId,
      false
    );
    
    // Record subscription for cleanup
    this.subscriptions.add(unsubscribe);
    
    return () => {
      unsubscribe();
      this.subscriptions.delete(unsubscribe);
    };
  }
  
  /**
   * Subscribe to event (once only)
   * 
   * @typeParam T - Event payload type
   * @param eventType - Event type
   * @param handler - Event handler function
   * @returns Unsubscribe function
   */
  once<T = unknown>(eventType: string, handler: GovernedEventHandler<T>): Unsubscribe {
    // Wrap handler to log events and handle once subscription
    const onceHandler: GovernedEventHandler<T> = (event) => {
      unsubscribe();
      this.eventLogger.log(event as GovernedEvent, 'receive');
      handler(event);
    };
    
    const unsubscribe = this.eventRouter.subscribe(
      eventType,
      onceHandler as GovernedEventHandler,
      this.pluginId,
      true
    );
    
    this.subscriptions.add(unsubscribe);
    
    return () => {
      unsubscribe();
      this.subscriptions.delete(unsubscribe);
    };
  }
  
  /**
   * Unsubscribe from event
   * 
   * @param eventType - Event type
   * @param handler - Event handler function
   */
  off<T = unknown>(eventType: string, _handler: GovernedEventHandler<T>): void {
    // Note: In current implementation, unsubscription requires the returned unsubscribe function
    // This method is mainly for compatibility, it's recommended to use the function returned by on()
    // The _handler parameter is kept for interface compliance but intentionally unused
    console.warn(
      '[EventBusCapability] off() method is deprecated, ' +
      'please use the unsubscribe function returned by on() to unsubscribe. ' +
      `EventType: ${eventType}`
    );
  }
  
  /**
   * Wait for event
   * 
   * Returns a Promise that resolves when the specified event is triggered
   * 
   * @typeParam T - Event payload type
   * @param eventType - Event type
   * @param timeout - Timeout in milliseconds, no timeout by default
   * @returns Event object
   */
  waitFor<T = unknown>(eventType: string, timeout?: number): Promise<GovernedEvent<T>> {
    return new Promise((resolve, reject) => {
      let timeoutId: ReturnType<typeof setTimeout> | null = null;
      
      const unsubscribe = this.once<T>(eventType, (event) => {
        if (timeoutId) {
          clearTimeout(timeoutId);
        }
        resolve(event);
      });
      
      if (timeout && timeout > 0) {
        timeoutId = setTimeout(() => {
          unsubscribe();
          reject(new Error(`Waiting for event ${eventType} timed out (${timeout}ms)`));
        }, timeout);
      }
    });
  }
  
  /**
   * Get recent events
   * 
   * @param eventType - Event type (optional, returns all if not provided)
   * @param limit - Limit on number of events returned
   * @returns List of recent events
   */
  getRecentEvents(eventType?: string, limit?: number): GovernedEvent[] {
    const logs = eventType 
      ? this.eventLogger.getByType(eventType, limit)
      : this.eventLogger.getRecentEvents(limit);
    
    return logs.map(entry => entry.event);
  }
  
  /**
   * Default Trace ID generator
   * 
   * @returns Unique Trace ID
   */
  private defaultTraceIdGenerator(): string {
    // Simple implementation: timestamp + random number
    const timestamp = Date.now().toString(36);
    const random = Math.random().toString(36).slice(2, 8);
    return `${timestamp}-${random}`;
  }

  // =========================================
  // Backpressure Management (v3.3.0)
  // =========================================

  /**
   * Configure backpressure settings
   * 
   * 配置背压设置
   * 
   * @param config - Backpressure configuration
   * @since 3.3.0
   */
  configureBackpressure(config: BackpressureConfig): void {
    this.backpressureManager.configure(config);
  }

  /**
   * Get current backpressure metrics
   * 
   * 获取当前背压指标
   * 
   * @returns Current backpressure metrics snapshot
   * @since 3.3.0
   */
  getBackpressureMetrics(): BackpressureMetrics {
    return this.backpressureManager.getMetrics();
  }

  /**
   * Check if backpressure is currently active
   * 
   * 检查背压是否当前处于激活状态
   * 
   * @param eventType - Optional event type to check specific queue
   * @returns True if backpressure limit is reached
   * @since 3.3.0
   */
  isBackpressureActive(eventType?: string): boolean {
    return this.backpressureManager.isActive(eventType);
  }

  /**
   * Reset backpressure metrics
   * 
   * 重置背压指标
   * 
   * @since 3.3.0
   */
  resetBackpressureMetrics(): void {
    this.backpressureManager.resetMetrics();
  }
  
  /**
   * Destroy capability instance
   */
  destroy(): void {
    // Unsubscribe from all subscriptions
    this.subscriptions.forEach(unsubscribe => unsubscribe());
    this.subscriptions.clear();
    // Clear backpressure manager state
    this.backpressureManager.clear();
  }
}
