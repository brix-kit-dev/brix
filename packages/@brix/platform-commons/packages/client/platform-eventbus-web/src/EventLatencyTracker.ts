/**
 * @file Event Latency Tracker
 * @description Records end-to-end latency from event publish to subscribe
 * @module @brix/platform-eventbus-web/EventLatencyTracker
 * @version 3.0.0
 * 
 * Architecture Overview:
 * EventLatencyTracker implements v3.0 architecture blueprint task 4.4-2:
 * Add event flow latency instrumentation in platform-eventbus-web.
 * 
 * Core Features:
 * 1. Record event publish timestamps
 * 2. Calculate end-to-end latency from publish to subscribe
 * 3. Compute latency distribution (P50, P95, P99)
 * 4. Provide latency metrics export interface
 * 
 * SLA Metrics:
 * - Event flow P99 latency < 200ms
 * - 100% cross-plugin events go through EventBus
 */

import type { GovernedEvent } from '@brix/runtime-sdk-api-web';

/**
 * Latency statistics result
 */
export interface LatencyStats {
  /**
   * Sample count
   */
  count: number;
  
  /**
   * Minimum latency (milliseconds)
   */
  min: number;
  
  /**
   * Maximum latency (milliseconds)
   */
  max: number;
  
  /**
   * Average latency (milliseconds)
   */
  avg: number;
  
  /**
   * P50 latency (milliseconds)
   */
  p50: number;
  
  /**
   * P95 latency (milliseconds)
   */
  p95: number;
  
  /**
   * P99 latency (milliseconds)
   */
  p99: number;
}

/**
 * Event latency record
 */
export interface EventLatencyRecord {
  /**
   * Event type
   */
  eventType: string;
  
  /**
   * Trace ID
   */
  traceId: string;
  
  /**
   * Publish timestamp
   */
  publishTime: number;
  
  /**
   * Receive timestamp
   */
  receiveTime: number;
  
  /**
   * Latency (milliseconds)
   */
  latencyMs: number;
  
  /**
   * Source plugin
   */
  sourcePlugin: string;
  
  /**
   * Target plugin
   */
  targetPlugin: string;
}

/**
 * Latency tracker configuration
 */
export interface EventLatencyTrackerConfig {
  /**
   * Whether to enable tracking
   * @default true
   */
  enabled?: boolean;
  
  /**
   * Number of history records to retain
   * @default 1000
   */
  maxHistorySize?: number;
  
  /**
   * Slow event threshold (milliseconds)
   * @default 100
   */
  slowEventThresholdMs?: number;
  
  /**
   * SLA P99 threshold (milliseconds)
   * @default 200
   */
  slaP99ThresholdMs?: number;
  
  /**
   * Metrics sampling rate (0.0 - 1.0)
   * @default 1.0
   */
  sampleRate?: number;
}

/**
 * Default configuration
 */
const DEFAULT_CONFIG: Required<EventLatencyTrackerConfig> = {
  enabled: true,
  maxHistorySize: 1000,
  slowEventThresholdMs: 100,
  slaP99ThresholdMs: 200,
  sampleRate: 1.0,
};

/**
 * Event Latency Tracker
 * 
 * Records and computes end-to-end latency from event publish to subscribe.
 * 
 * Usage Example:
 * ```typescript
 * const tracker = new EventLatencyTracker();
 * 
 * // Record at event publish
 * tracker.recordPublish(event);
 * 
 * // Record at event receive
 * tracker.recordReceive(event, receiverPluginId);
 * 
 * // Get statistics
 * const stats = tracker.getStats();
 * console.log(`P99 latency: ${stats.p99}ms`);
 * 
 * // Check SLA compliance
 * const slaOk = tracker.checkSlaCompliance();
 * ```
 */
export class EventLatencyTracker {
  /**
   * Configuration
   */
  private config: Required<EventLatencyTrackerConfig>;
  
  /**
   * Latency record history
   */
  private history: EventLatencyRecord[] = [];
  
  /**
   * Latency samples (for percentile calculation)
   */
  private latencySamples: number[] = [];
  
  /**
   * Latency statistics by event type
   */
  private statsByType: Map<string, number[]> = new Map();
  
  /**
   * SLA violation count
   */
  private slaViolationCount = 0;
  
  /**
   * Slow event count
   */
  private slowEventCount = 0;
  
  /**
   * Callback: when SLA violation detected
   */
  onSlaViolation?: (record: EventLatencyRecord) => void;
  
  /**
   * Constructor
   * 
   * @param config - Configuration options
   */
  constructor(config?: EventLatencyTrackerConfig) {
    this.config = { ...DEFAULT_CONFIG, ...config };
  }
  
  /**
   * Record event publish
   * 
   * At publish time, event's metadata.timestamp is already set.
   * This method is mainly for additional tracking (if needed).
   * 
   * @param _event - Event object (unused, reserved for future tracking)
   */
  recordPublish(_event: GovernedEvent): void {
    if (!this.config.enabled) {
      return;
    }
    
    // Publish timestamp is already in metadata.timestamp
    // This method can be used for additional preprocessing (e.g., sampling decision)
  }
  
  /**
   * Record event receive and calculate latency
   * 
   * @param event - Event object
   * @param targetPlugin - Target plugin ID
   */
  recordReceive(event: GovernedEvent, targetPlugin: string): void {
    if (!this.config.enabled) {
      return;
    }
    
    // Sampling check
    if (this.config.sampleRate < 1.0 && Math.random() > this.config.sampleRate) {
      return;
    }
    
    const receiveTime = Date.now();
    const publishTime = event.metadata?.timestamp ?? receiveTime;
    const latencyMs = receiveTime - publishTime;
    
    const record: EventLatencyRecord = {
      eventType: event.type,
      traceId: event.metadata?.eventId ?? 'unknown',
      publishTime,
      receiveTime,
      latencyMs,
      sourcePlugin: event.metadata?.source ?? 'unknown',
      targetPlugin,
    };
    
    // Add to history
    this.history.push(record);
    if (this.history.length > this.config.maxHistorySize) {
      this.history.shift();
    }
    
    // Add to samples
    this.latencySamples.push(latencyMs);
    if (this.latencySamples.length > this.config.maxHistorySize) {
      this.latencySamples.shift();
    }
    
    // Statistics by type
    let typeSamples = this.statsByType.get(event.type);
    if (!typeSamples) {
      typeSamples = [];
      this.statsByType.set(event.type, typeSamples);
    }
    typeSamples.push(latencyMs);
    if (typeSamples.length > this.config.maxHistorySize / 10) {
      typeSamples.shift();
    }
    
    // Check for slow events
    if (latencyMs >= this.config.slowEventThresholdMs) {
      this.slowEventCount++;
      console.warn(
        `[EventLatencyTracker] Slow event detected: type=${event.type}, latency=${latencyMs}ms, ` +
        `source=${record.sourcePlugin}, target=${targetPlugin}`
      );
    }
    
    // Check SLA violation
    const stats = this.getStats();
    if (stats.p99 > this.config.slaP99ThresholdMs) {
      this.slaViolationCount++;
      this.onSlaViolation?.(record);
    }
  }
  
  /**
   * Get latency statistics
   * 
   * @returns Latency statistics result
   */
  getStats(): LatencyStats {
    if (this.latencySamples.length === 0) {
      return {
        count: 0,
        min: 0,
        max: 0,
        avg: 0,
        p50: 0,
        p95: 0,
        p99: 0,
      };
    }
    
    const sorted = [...this.latencySamples].sort((a, b) => a - b);
    const count = sorted.length;
    
    return {
      count,
      min: sorted[0],
      max: sorted[count - 1],
      avg: sorted.reduce((a, b) => a + b, 0) / count,
      p50: this.percentile(sorted, 50),
      p95: this.percentile(sorted, 95),
      p99: this.percentile(sorted, 99),
    };
  }
  
  /**
   * Get latency statistics for a specific event type
   * 
   * @param eventType - Event type
   * @returns Latency statistics result
   */
  getStatsByType(eventType: string): LatencyStats | null {
    const samples = this.statsByType.get(eventType);
    if (!samples || samples.length === 0) {
      return null;
    }
    
    const sorted = [...samples].sort((a, b) => a - b);
    const count = sorted.length;
    
    return {
      count,
      min: sorted[0],
      max: sorted[count - 1],
      avg: sorted.reduce((a, b) => a + b, 0) / count,
      p50: this.percentile(sorted, 50),
      p95: this.percentile(sorted, 95),
      p99: this.percentile(sorted, 99),
    };
  }
  
  /**
   * Check SLA compliance
   * 
   * @returns Whether SLA is compliant
   */
  checkSlaCompliance(): boolean {
    const stats = this.getStats();
    return stats.p99 <= this.config.slaP99ThresholdMs;
  }
  
  /**
   * Get SLA violation count
   * 
   * @returns Violation count
   */
  getSlaViolationCount(): number {
    return this.slaViolationCount;
  }
  
  /**
   * Get slow event count
   * 
   * @returns Slow event count
   */
  getSlowEventCount(): number {
    return this.slowEventCount;
  }
  
  /**
   * Get latency record history
   * 
   * @param limit - Return count limit
   * @returns List of latency records
   */
  getHistory(limit?: number): EventLatencyRecord[] {
    if (limit) {
      return this.history.slice(-limit);
    }
    return [...this.history];
  }
  
  /**
   * Export Prometheus format metrics
   * 
   * @returns Prometheus format metrics string
   */
  exportPrometheusMetrics(): string {
    const stats = this.getStats();
    const lines: string[] = [];
    
    // Latency distribution
    lines.push(`# HELP brix_eventbus_latency_seconds Event bus latency distribution (seconds)`);
    lines.push(`# TYPE brix_eventbus_latency_seconds summary`);
    lines.push(`brix_eventbus_latency_seconds{quantile="0.5"} ${stats.p50 / 1000}`);
    lines.push(`brix_eventbus_latency_seconds{quantile="0.95"} ${stats.p95 / 1000}`);
    lines.push(`brix_eventbus_latency_seconds{quantile="0.99"} ${stats.p99 / 1000}`);
    lines.push(`brix_eventbus_latency_seconds_sum ${stats.avg * stats.count / 1000}`);
    lines.push(`brix_eventbus_latency_seconds_count ${stats.count}`);
    
    // Slow event count
    lines.push(`# HELP brix_eventbus_slow_events_total Total slow events`);
    lines.push(`# TYPE brix_eventbus_slow_events_total counter`);
    lines.push(`brix_eventbus_slow_events_total ${this.slowEventCount}`);
    
    // SLA violation count
    lines.push(`# HELP brix_eventbus_sla_violations_total Total SLA violations`);
    lines.push(`# TYPE brix_eventbus_sla_violations_total counter`);
    lines.push(`brix_eventbus_sla_violations_total ${this.slaViolationCount}`);
    
    return lines.join('\n');
  }
  
  /**
   * Reset statistics data
   */
  reset(): void {
    this.history = [];
    this.latencySamples = [];
    this.statsByType.clear();
    this.slaViolationCount = 0;
    this.slowEventCount = 0;
  }
  
  /**
   * Calculate percentile
   * 
   * @param sorted - Sorted sample array
   * @param percentile - Percentile (0-100)
   * @returns Percentile value
   */
  private percentile(sorted: number[], percentile: number): number {
    if (sorted.length === 0) return 0;
    const index = Math.ceil((percentile / 100) * sorted.length) - 1;
    return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
  }
}

/**
 * Create event latency tracker instance
 * 
 * @param config - Configuration options
 * @returns Tracker instance
 */
export function createEventLatencyTracker(
  config?: EventLatencyTrackerConfig
): EventLatencyTracker {
  return new EventLatencyTracker(config);
}
