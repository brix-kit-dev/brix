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
 * @file Event Logger
 * @description Records all events for debugging and observability
 * @module @brix-sdk/platform-eventbus-web/EventLogger
 * @version 3.0.0
 * 
 * Architecture Overview:
 * EventLogger is responsible for recording all events passing through the event bus.
 * Supports event replay, debugging, and observability.
 * 
 * Features:
 * 1. Record event history (with capacity limit)
 * 2. Support querying by event type and time range
 * 3. Configurable log level
 * 4. Support exporting event logs
 */

import type { GovernedEvent } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Log level
 */
export type LogLevel = 'debug' | 'info' | 'warn' | 'error' | 'none';

/**
 * Event logger configuration
 */
export interface EventLoggerConfig {
  /**
   * Whether to enable logging
   * @default true
   */
  enabled?: boolean;
  
  /**
   * Maximum number of log entries
   * @default 100
   */
  maxSize?: number;
  
  /**
   * Log level
   * @default 'info'
   */
  logLevel?: LogLevel;
  
  /**
   * Whether to output to console
   * @default false
   */
  consoleOutput?: boolean;
  
  /**
   * Custom log handler
   */
  customHandler?: (event: GovernedEvent, action: 'emit' | 'receive') => void;
}

/**
 * Event log entry
 */
export interface EventLogEntry {
  /**
   * Log ID
   */
  id: string;
  
  /**
   * Event object
   */
  event: GovernedEvent;
  
  /**
   * Action type
   */
  action: 'emit' | 'receive';
  
  /**
   * Recorded timestamp
   */
  recordedAt: number;
  
  /**
   * Number of plugins that received this event
   */
  receiverCount?: number;
}

/**
 * Event Logger
 * 
 * Records all events for debugging and observability.
 * 
 * Usage Example:
 * ```typescript
 * const logger = new EventLogger({
 *   enabled: true,
 *   maxSize: 100,
 *   consoleOutput: true,
 * });
 * 
 * // Log event
 * logger.log(event, 'emit');
 * 
 * // Query recent events
 * const recentEvents = logger.getRecentEvents(10);
 * 
 * // Query by type
 * const bookingEvents = logger.getByType('booking:*');
 * ```
 */
export class EventLogger {
  /**
   * Event log buffer
   */
  private logs: EventLogEntry[] = [];
  
  /**
   * Configuration
   */
  private config: Required<EventLoggerConfig>;
  
  /**
   * Log ID counter
   */
  private idCounter: number = 0;
  
  /**
   * Constructor
   * 
   * @param config - Logger configuration
   */
  constructor(config: EventLoggerConfig = {}) {
    this.config = {
      enabled: config.enabled ?? true,
      maxSize: config.maxSize ?? 100,
      logLevel: config.logLevel ?? 'info',
      consoleOutput: config.consoleOutput ?? false,
      customHandler: config.customHandler ?? undefined as unknown as (event: GovernedEvent, action: 'emit' | 'receive') => void,
    };
  }
  
  /**
   * Log event
   * 
   * @param event - Event object
   * @param action - Action type
   * @param receiverCount - Number of receivers
   */
  log(event: GovernedEvent, action: 'emit' | 'receive', receiverCount?: number): void {
    if (!this.config.enabled) {
      return;
    }
    
    const entry: EventLogEntry = {
      id: this.generateId(),
      event,
      action,
      recordedAt: Date.now(),
      receiverCount,
    };
    
    // Add to log buffer
    this.logs.push(entry);
    
    // Remove oldest record when capacity exceeded
    if (this.logs.length > this.config.maxSize) {
      this.logs.shift();
    }
    
    // Console output
    if (this.config.consoleOutput) {
      this.outputToConsole(entry);
    }
    
    // Custom handler
    if (this.config.customHandler) {
      try {
        this.config.customHandler(event, action);
      } catch (error) {
        console.error('[EventLogger] Custom handler execution error:', error);
      }
    }
  }
  
  /**
   * Get recent event logs
   * 
   * @param limit - Return count limit
   * @returns Event log array (newest first)
   */
  getRecentEvents(limit?: number): EventLogEntry[] {
    const reversed = [...this.logs].reverse();
    
    if (limit && limit > 0) {
      return reversed.slice(0, limit);
    }
    
    return reversed;
  }
  
  /**
   * Query by event type
   * 
   * @param pattern - Event type pattern (supports * wildcard)
   * @param limit - Return count limit
   * @returns Matching event logs
   */
  getByType(pattern: string, limit?: number): EventLogEntry[] {
    let results: EventLogEntry[];
    
    if (pattern.includes('*')) {
      // Wildcard matching
      const prefix = pattern.replace('*', '');
      results = this.logs.filter(entry => entry.event.type.startsWith(prefix));
    } else {
      // Exact matching
      results = this.logs.filter(entry => entry.event.type === pattern);
    }
    
    results = results.reverse();
    
    if (limit && limit > 0) {
      return results.slice(0, limit);
    }
    
    return results;
  }
  
  /**
   * Query by time range
   * 
   * @param startTime - Start timestamp
   * @param endTime - End timestamp
   * @returns Matching event logs
   */
  getByTimeRange(startTime: number, endTime: number): EventLogEntry[] {
    return this.logs.filter(entry => 
      entry.recordedAt >= startTime && entry.recordedAt <= endTime
    ).reverse();
  }
  
  /**
   * Query by source plugin
   * 
   * @param pluginId - Plugin ID
   * @param limit - Return count limit
   * @returns Matching event logs
   */
  getBySource(pluginId: string, limit?: number): EventLogEntry[] {
    let results = this.logs.filter(
      entry => entry.event.metadata.source === pluginId
    ).reverse();
    
    if (limit && limit > 0) {
      results = results.slice(0, limit);
    }
    
    return results;
  }
  
  /**
   * Clear logs
   */
  clear(): void {
    this.logs = [];
    this.idCounter = 0;
  }
  
  /**
   * Export logs
   * 
   * @returns Log data in JSON format
   */
  export(): string {
    return JSON.stringify(this.logs, null, 2);
  }
  
  /**
   * Update configuration
   * 
   * @param config - Partial configuration
   */
  updateConfig(config: Partial<EventLoggerConfig>): void {
    this.config = { ...this.config, ...config };
  }
  
  /**
   * Get log statistics
   * 
   * @returns Log statistics
   */
  getStats(): {
    totalCount: number;
    emitCount: number;
    receiveCount: number;
    eventTypes: Record<string, number>;
  } {
    const stats = {
      totalCount: this.logs.length,
      emitCount: 0,
      receiveCount: 0,
      eventTypes: {} as Record<string, number>,
    };
    
    for (const entry of this.logs) {
      if (entry.action === 'emit') {
        stats.emitCount++;
      } else {
        stats.receiveCount++;
      }
      
      const type = entry.event.type;
      stats.eventTypes[type] = (stats.eventTypes[type] ?? 0) + 1;
    }
    
    return stats;
  }
  
  /**
   * Generate log ID
   * 
   * @returns Unique ID
   */
  private generateId(): string {
    return `evt-${Date.now()}-${++this.idCounter}`;
  }
  
  /**
   * Output to console
   * 
   * @param entry - Log entry
   */
  private outputToConsole(entry: EventLogEntry): void {
    const { event, action, receiverCount } = entry;
    const { type, metadata } = event;
    
    const prefix = action === 'emit' ? '??' : '??';
    const source = metadata.source;
    const scope = metadata.scope;
    
    console.log(
      `${prefix} [EventBus] ${action.toUpperCase()} ${type}`,
      {
        source,
        scope,
        payload: event.payload,
        eventId: metadata.eventId,
        receiverCount,
      }
    );
  }
}
