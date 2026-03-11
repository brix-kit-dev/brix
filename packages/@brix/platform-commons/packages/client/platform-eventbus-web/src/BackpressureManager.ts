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
 * @file Backpressure Manager
 * @description Manages event queue backpressure and overflow strategies
 * @module @brix/platform-eventbus-web/BackpressureManager
 * @version 3.3.0
 * @since 3.3.0
 *
 * Architecture Overview:
 * BackpressureManager provides queue management and overflow handling
 * to prevent memory exhaustion under high event load.
 *
 * 架构概述：
 * BackpressureManager 提供队列管理和溢出处理，
 * 以防止高事件负载下的内存耗尽。
 *
 * Core Responsibilities:
 * 1. Track event queue depth per event type
 * 2. Apply overflow strategies when limits are reached
 * 3. Emit warning events when thresholds are crossed
 * 4. Provide metrics for monitoring
 */

import type {
  BackpressureConfig,
  BackpressureMetrics,
  GovernedEvent,
} from '@brix/runtime-sdk-api-web';
import { BackpressureError } from '@brix/runtime-sdk-api-web';

/**
 * Queued event entry
 */
interface QueuedEvent {
  event: GovernedEvent;
  timestamp: number;
}

/**
 * Default backpressure configuration
 */
const DEFAULT_CONFIG: Required<BackpressureConfig> = {
  maxQueueDepth: 1000,
  globalMaxQueueDepth: 10000,
  overflowStrategy: 'drop-oldest',
  warningThreshold: 80,
  enableMetrics: true,
};

/**
 * Backpressure Manager
 *
 * Manages event queue depth and applies overflow strategies.
 *
 * 管理事件队列深度并应用溢出策略。
 *
 * @example
 * ```typescript
 * const manager = new BackpressureManager({
 *   maxQueueDepth: 500,
 *   overflowStrategy: 'drop-oldest'
 * });
 *
 * // Before publishing
 * const result = manager.checkAndApply('booking:created', event);
 * if (result.accepted) {
 *   // Proceed with publish
 * }
 *
 * // After event is processed
 * manager.markProcessed('booking:created');
 * ```
 */
export class BackpressureManager {
  private config: Required<BackpressureConfig>;

  /**
   * Event queues per type
   * 每种类型的事件队列
   */
  private queues: Map<string, QueuedEvent[]> = new Map();

  /**
   * Total events across all queues
   * 所有队列中的事件总数
   */
  private totalQueueDepth: number = 0;

  /**
   * Metrics tracking
   * 指标跟踪
   */
  private metrics: {
    droppedCount: number;
    rejectedCount: number;
    warningCount: number;
    lastWarningTimestamp: number | undefined;
  } = {
    droppedCount: 0,
    rejectedCount: 0,
    warningCount: 0,
    lastWarningTimestamp: undefined,
  };

  /**
   * Warning callback
   */
  private onWarning?: (eventType: string, queueDepth: number, threshold: number) => void;

  /**
   * Create a new BackpressureManager
   *
   * @param config - Backpressure configuration
   * @param onWarning - Callback when warning threshold is reached
   */
  constructor(
    config?: BackpressureConfig,
    onWarning?: (eventType: string, queueDepth: number, threshold: number) => void
  ) {
    this.config = { ...DEFAULT_CONFIG, ...config };
    this.onWarning = onWarning;
  }

  /**
   * Configure backpressure settings
   *
   * @param config - New configuration (merged with existing)
   */
  configure(config: BackpressureConfig): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Check if event can be accepted and apply overflow strategy if needed
   *
   * 检查事件是否可以被接受，如果需要则应用溢出策略
   *
   * @param eventType - Event type
   * @param event - The event to check
   * @returns Result indicating if event was accepted and any dropped events
   */
  checkAndApply(
    eventType: string,
    event: GovernedEvent
  ): { accepted: boolean; droppedEvents: GovernedEvent[] } {
    const queue = this.getOrCreateQueue(eventType);
    const queueDepth = queue.length;
    const droppedEvents: GovernedEvent[] = [];

    // Check warning threshold
    // 检查警告阈值
    const warningLimit = Math.floor(
      (this.config.maxQueueDepth * this.config.warningThreshold) / 100
    );
    if (queueDepth >= warningLimit && queueDepth < this.config.maxQueueDepth) {
      this.emitWarning(eventType, queueDepth, warningLimit);
    }

    // Check if queue is at capacity
    // 检查队列是否已满
    if (queueDepth >= this.config.maxQueueDepth) {
      return this.applyOverflowStrategy(eventType, event, queue, droppedEvents);
    }

    // Check global limit
    // 检查全局限制
    if (this.totalQueueDepth >= this.config.globalMaxQueueDepth) {
      return this.applyGlobalOverflowStrategy(eventType, event, droppedEvents);
    }

    // Accept the event
    // 接受事件
    this.enqueue(eventType, event);
    return { accepted: true, droppedEvents };
  }

  /**
   * Mark an event as processed (dequeue)
   *
   * @param eventType - Event type
   */
  markProcessed(eventType: string): void {
    const queue = this.queues.get(eventType);
    if (queue && queue.length > 0) {
      queue.shift();
      this.totalQueueDepth = Math.max(0, this.totalQueueDepth - 1);
    }
  }

  /**
   * Get current backpressure metrics
   *
   * @returns Current metrics snapshot
   */
  getMetrics(): BackpressureMetrics {
    const queueDepthByType = new Map<string, number>();
    for (const [type, queue] of this.queues) {
      queueDepthByType.set(type, queue.length);
    }

    const utilizationPercent =
      this.config.globalMaxQueueDepth > 0
        ? (this.totalQueueDepth / this.config.globalMaxQueueDepth) * 100
        : 0;

    return {
      queueDepthByType,
      totalQueueDepth: this.totalQueueDepth,
      droppedCount: this.metrics.droppedCount,
      rejectedCount: this.metrics.rejectedCount,
      warningCount: this.metrics.warningCount,
      lastWarningTimestamp: this.metrics.lastWarningTimestamp,
      utilizationPercent: Math.min(100, utilizationPercent),
    };
  }

  /**
   * Check if backpressure is currently active
   *
   * @param eventType - Optional event type to check
   * @returns True if backpressure is active
   */
  isActive(eventType?: string): boolean {
    if (eventType) {
      const queue = this.queues.get(eventType);
      return queue !== undefined && queue.length >= this.config.maxQueueDepth;
    }
    return this.totalQueueDepth >= this.config.globalMaxQueueDepth;
  }

  /**
   * Reset metrics
   */
  resetMetrics(): void {
    this.metrics = {
      droppedCount: 0,
      rejectedCount: 0,
      warningCount: 0,
      lastWarningTimestamp: undefined,
    };
  }

  /**
   * Clear all queues
   */
  clear(): void {
    this.queues.clear();
    this.totalQueueDepth = 0;
    this.resetMetrics();
  }

  /**
   * Get current configuration
   */
  getConfig(): Required<BackpressureConfig> {
    return { ...this.config };
  }

  // =========================================
  // Private Methods
  // =========================================

  private getOrCreateQueue(eventType: string): QueuedEvent[] {
    let queue = this.queues.get(eventType);
    if (!queue) {
      queue = [];
      this.queues.set(eventType, queue);
    }
    return queue;
  }

  private enqueue(eventType: string, event: GovernedEvent): void {
    const queue = this.getOrCreateQueue(eventType);
    queue.push({
      event,
      timestamp: Date.now(),
    });
    this.totalQueueDepth++;
  }

  private applyOverflowStrategy(
    eventType: string,
    event: GovernedEvent,
    queue: QueuedEvent[],
    droppedEvents: GovernedEvent[]
  ): { accepted: boolean; droppedEvents: GovernedEvent[] } {
    const strategy = this.config.overflowStrategy;

    switch (strategy) {
      case 'drop-oldest': {
        // Drop the oldest event and add the new one
        // 丢弃最旧的事件并添加新事件
        const dropped = queue.shift();
        if (dropped) {
          droppedEvents.push(dropped.event);
          this.metrics.droppedCount++;
          this.totalQueueDepth--;
        }
        this.enqueue(eventType, event);
        return { accepted: true, droppedEvents };
      }

      case 'reject': {
        // Reject the new event
        // 拒绝新事件
        this.metrics.rejectedCount++;
        throw new BackpressureError(eventType, queue.length, this.config.maxQueueDepth);
      }

      case 'block': {
        // In sync context, we cannot truly block, so we reject
        // 在同步上下文中，我们无法真正阻塞，所以拒绝
        this.metrics.rejectedCount++;
        return { accepted: false, droppedEvents };
      }

      default: {
        // Unknown strategy, use drop-oldest as fallback
        // 未知策略，使用 drop-oldest 作为后备
        const dropped = queue.shift();
        if (dropped) {
          droppedEvents.push(dropped.event);
          this.metrics.droppedCount++;
          this.totalQueueDepth--;
        }
        this.enqueue(eventType, event);
        return { accepted: true, droppedEvents };
      }
    }
  }

  private applyGlobalOverflowStrategy(
    eventType: string,
    event: GovernedEvent,
    droppedEvents: GovernedEvent[]
  ): { accepted: boolean; droppedEvents: GovernedEvent[] } {
    const strategy = this.config.overflowStrategy;

    if (strategy === 'reject' || strategy === 'block') {
      this.metrics.rejectedCount++;
      if (strategy === 'reject') {
        throw new BackpressureError(
          eventType,
          this.totalQueueDepth,
          this.config.globalMaxQueueDepth
        );
      }
      return { accepted: false, droppedEvents };
    }

    // drop-oldest: Find the oldest event across all queues
    // drop-oldest: 在所有队列中找到最旧的事件
    let oldestTimestamp = Infinity;
    let oldestType: string | null = null;

    for (const [type, queue] of this.queues) {
      if (queue.length > 0 && queue[0].timestamp < oldestTimestamp) {
        oldestTimestamp = queue[0].timestamp;
        oldestType = type;
      }
    }

    if (oldestType) {
      const queue = this.queues.get(oldestType);
      if (queue) {
        const dropped = queue.shift();
        if (dropped) {
          droppedEvents.push(dropped.event);
          this.metrics.droppedCount++;
          this.totalQueueDepth--;
        }
      }
    }

    this.enqueue(eventType, event);
    return { accepted: true, droppedEvents };
  }

  private emitWarning(eventType: string, queueDepth: number, threshold: number): void {
    this.metrics.warningCount++;
    this.metrics.lastWarningTimestamp = Date.now();
    this.onWarning?.(eventType, queueDepth, threshold);
  }
}
