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
 * @file Event type definitions
 * @description Cross-platform shared event type definitions
 * @module @brix/platform-shared/types/events
 * @version 3.3.0
 *
 * Architecture Note (v3.3.0 Phase 3 Contract Layer Cleanup):
 * All event contract types are now defined in @brix/runtime-sdk-api-web and re-exported here.
 * This eliminates type duplication and establishes runtime-sdk-api-web as the single source of truth.
 *
 * Migrated Types (LL3 compliance):
 * - BaseEvent: Base event interface
 * - MetadataEvent: Event with metadata
 * - EventMetadata: Event metadata structure
 * - EventPriority: Event priority levels
 * - EventSubscriptionOptions: Extended subscription options
 * - EventPublishOptions: Event publish options
 *
 * @see {@link https://github.com/brix-framework/runtime-sdk | @brix/runtime-sdk-api-web}
 */

// ============================================================
// Re-export all event types from runtime-sdk-api-web (authoritative source)
// ============================================================

/**
 * Re-export BaseEvent from the authoritative source.
 *
 * Base event interface defining common fields for all events.
 * @see {@link @brix/runtime-sdk-api-web/types/event | BaseEvent}
 * @since 3.3.0 Migrated to runtime-sdk-api-web
 */
export { type BaseEvent } from '@brix/runtime-sdk-api-web';

/**
 * Re-export MetadataEvent from the authoritative source.
 *
 * Event with rich metadata for observability and governance.
 * @see {@link @brix/runtime-sdk-api-web/types/event | MetadataEvent}
 * @since 3.3.0 Migrated to runtime-sdk-api-web
 */
export { type MetadataEvent } from '@brix/runtime-sdk-api-web';

/**
 * Re-export EventMetadata from the authoritative source.
 *
 * Metadata structure for event tracing and observability.
 * @see {@link @brix/runtime-sdk-api-web/types/event | EventMetadata}
 */
export { type EventMetadata } from '@brix/runtime-sdk-api-web';

/**
 * Re-export EventPriority from the authoritative source.
 *
 * Priority levels for event processing order.
 * @see {@link @brix/runtime-sdk-api-web/types/event | EventPriority}
 * @since 3.3.0 Migrated to runtime-sdk-api-web
 */
export { type EventPriority } from '@brix/runtime-sdk-api-web';

/**
 * Re-export EventSubscriptionOptions from the authoritative source.
 *
 * Extended subscription options with priority and filtering.
 * @see {@link @brix/runtime-sdk-api-web/types/event | EventSubscriptionOptions}
 * @since 3.3.0 Migrated to runtime-sdk-api-web
 */
export { type EventSubscriptionOptions } from '@brix/runtime-sdk-api-web';

/**
 * Re-export EventPublishOptions from the authoritative source.
 *
 * Options for event publishing behavior (async, delay, debounce, throttle).
 * @see {@link @brix/runtime-sdk-api-web/types/event | EventPublishOptions}
 * @since 3.3.0 Migrated to runtime-sdk-api-web
 */
export { type EventPublishOptions } from '@brix/runtime-sdk-api-web';

