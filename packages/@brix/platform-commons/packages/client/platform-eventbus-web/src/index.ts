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
 * @file platform-eventbus-web Module Entry
 * @description Web Event Bus Capability Implementation Module - Implements GovernedEventBusCapability Interface
 * @module @brix-sdk/platform-eventbus-web
 * @version 3.0.0
 * 
 * Module Description:
 * platform-eventbus-web is the implementation module for GovernedEventBusCapability interface.
 * Provides governable and traceable event communication capabilities.
 * 
 * Architectural Position:
 * ```text
 * +-------------------------------------------------------------------------+
 * | Capability Contract Layer (runtime-sdk-api-web)                        |
 * | +-- GovernedEventBusCapability Interface Definition                    |
 * +-------------------------------------------------------------------------+
 * | Capability Implementation Layer (platform-commons)                     |
 * | +-- platform-eventbus-web (this module) ?                             |
 * |      +-- EventBusCapabilityImpl (interface implementation)             |
 * |      +-- EventRouter (event routing)                                   |
 * |      +-- EventLogger (event logging)                                   |
 * +-------------------------------------------------------------------------+
 * ```
 * 
 * Key Design:
 * 1. Events automatically injected with metadata (sourcePlugin, traceId, timestamp)
 * 2. Route events based on scope (plugin/host)
 * 3. Support event logging and tracing
 * 
 * Architectural Constraints:
 * ? Do not use window.dispatchEvent
 * ? Do not send events without metadata
 * ? All events must be sent through GovernedEventBusCapability
 */

// ============================================================================
// Capability Implementation
// ============================================================================

export { EventBusCapabilityImpl, type EventBusCapabilityConfig } from './EventBusCapabilityImpl';

// ============================================================================
// Core Components
// ============================================================================

export { EventRouter } from './EventRouter';
export { EventLogger, type EventLoggerConfig, type EventLogEntry, type LogLevel } from './EventLogger';
export { 
  EventLatencyTracker, 
  createEventLatencyTracker,
  type EventLatencyTrackerConfig,
  type EventLatencyRecord,
  type LatencyStats,
} from './EventLatencyTracker';

// ============================================================================
// Backpressure Management (v3.3.0)
// ============================================================================

export { BackpressureManager } from './BackpressureManager';

// Re-export backpressure types from API for convenience
// 为方便起见，从 API 重新导出背压类型
export type {
  BackpressureConfig,
  BackpressureMetrics,
  BackpressureOverflowStrategy,
} from '@brix-sdk/runtime-sdk-api-web';
export { BackpressureError } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Common Types
// ============================================================================

export type { Unsubscribe } from './EventRouter';
