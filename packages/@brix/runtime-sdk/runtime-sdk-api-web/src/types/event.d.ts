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
 * @module @brix-sdk/runtime-sdk-api-web/types/event
 * @version 3.2.0
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file.
 *
 * [Design Principles]
 * - Supports both simple Event Bus and Governed Event Bus modes
 * - Governed Event Bus provides complete event metadata and audit information
 */
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
/**
 * Event Subscription Options
 */
export interface SubscriptionOptions {
    /** Whether to trigger only once */
    readonly once?: boolean;
    /** Filter function */
    readonly filter?: (payload: unknown) => boolean;
}
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
/**
 * Event Bus Capability Type Identifier
 */
export declare const EventBusCapabilityType: unique symbol;
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
/**
 * Governed Event Bus Capability Type Identifier
 */
export declare const GovernedEventBusCapabilityType: unique symbol;
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
}
//# sourceMappingURL=event.d.ts.map