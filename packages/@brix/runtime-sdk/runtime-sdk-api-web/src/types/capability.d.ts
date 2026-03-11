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
 * @file Capability Related Type Definitions
 * @description Defines core types for the capability system, including capability metadata, status, registry, etc.
 * @module @brix/runtime-sdk-api-web/types/capability
 * @version 3.2.0
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file to keep the contract layer clean.
 *
 * [Design Principles]
 * - All type definitions use readonly properties
 * - Use Symbol as capability unique identifier (type-safe)
 * - Support lazy initialization and scope control for capabilities
 */
/**
 * Capability Priority Enum
 *
 * <p>Used to determine which implementation to use when multiple capability providers conflict.</p>
 */
export declare enum CapabilityPriority {
    /** Low Priority (fallback implementation) */
    LOW = 0,
    /** Normal Priority (default) */
    NORMAL = 50,
    /** High Priority (overrides default implementation) */
    HIGH = 100
}
/**
 * Capability Metadata (Simplified)
 */
export interface CapabilityMetadata {
    /** Capability Name */
    readonly name: string;
    /** Capability Version */
    readonly version: string;
    /** Priority */
    readonly priority: CapabilityPriority;
    /** Whether Required */
    readonly required: boolean;
}
/**
 * Capability ID Type
 *
 * <p>Supports string or Symbol, recommend using Symbol.for() to ensure uniqueness.</p>
 */
export type CapabilityId = string | symbol;
/**
 * Capability Meta Information (Full Version)
 *
 * <p>Contains complete metadata for capability registration and discovery.</p>
 */
export interface CapabilityMeta {
    /** Capability Unique Identifier */
    readonly id: CapabilityId;
    /** Capability Name (Human-readable) */
    readonly name: string;
    /** Capability Description */
    readonly description?: string;
    /** Capability Version */
    readonly version?: string;
    /** Dependent Capability ID List */
    readonly dependencies?: CapabilityId[];
    /** Tags (for filtering and categorization) */
    readonly tags?: string[];
}
/**
 * Capability Status
 *
 * <p>Describes the current status of a capability in its lifecycle.</p>
 */
export type CapabilityStatus = 'registered' | 'initializing' | 'ready' | 'error' | 'disposed';
/**
 * Capability Type Identifier (Generic Interface)
 *
 * <p>Used to identify and create capability instances, containing capability metadata.
 * Uses phantom property `_phantom` for type inference.</p>
 *
 * @example
 * ```typescript
 * interface MyCapability {
 *   doSomething(): void;
 * }
 *
 * const MyCapabilityType = createCapabilityType<MyCapability>({
 *   id: 'my-capability',
 *   name: 'My Capability',
 * });
 * ```
 */
export interface CapabilityType<T = unknown> extends CapabilityMeta {
    /**
     * Phantom property for type inference
     * Does not exist at runtime, only for TypeScript type system
     */
    readonly _phantom?: T;
}
/**
 * Create Capability Type Identifier
 *
 * @param meta - Capability meta information
 * @returns Capability type identifier object
 */
export declare function createCapabilityType<T>(meta: Omit<CapabilityMeta, 'id'> & {
    id: string;
}): CapabilityType<T>;
/**
 * Capability Provider Interface
 *
 * <p>Encapsulates the creation and disposal logic of capability instances.</p>
 */
export interface CapabilityProvider<T = unknown> {
    /** Get capability instance */
    provide(): T;
    /** Dispose capability instance (optional) */
    dispose?(): void;
}
/**
 * Simple Capability Provider
 *
 * <p>Can be the capability instance itself, or a factory function that returns the capability instance.</p>
 */
export type SimpleCapabilityProvider<T> = T | (() => T);
/**
 * Capability Registration Options
 */
export interface CapabilityRegisterOptions {
    /**
     * Whether to override existing capability
     */
    override?: boolean;
    /**
     * Capability priority
     */
    priority?: CapabilityPriority;
    /**
     * Capability scope
     * - 'global': Global scope, shared by all modules
     * - 'module': Module scope, only available in current module
     * @default 'global'
     */
    scope?: 'global' | 'module';
    /**
     * Lazy initialization
     * When true, capability is instantiated on first use
     * @default false
     */
    lazy?: boolean;
}
/**
 * Capability Runtime Information
 *
 * <p>Describes the detailed status of a capability at runtime.</p>
 */
export interface CapabilityRuntimeInfo {
    /** Capability Metadata */
    readonly meta: CapabilityMeta;
    /** Current Status */
    readonly status: CapabilityStatus;
    /** Registration Timestamp */
    readonly registeredAt: number;
    /** Initialization Timestamp */
    readonly initializedAt?: number;
    /** Error Information (when status is error) */
    readonly error?: Error;
    /** Invocation Count Statistics */
    readonly invocationCount: number;
}
/**
 * Capability Registry Interface
 *
 * <p>Manages registration, retrieval, initialization, and disposal of capabilities.</p>
 */
export interface CapabilityRegistry {
    /** Get capability instance */
    get<T>(capabilityType: CapabilityType<T>): T | undefined;
    /** Get required capability (throws exception if not found) */
    getRequired<T>(capabilityType: CapabilityType<T>): T;
    /** Register capability */
    register<T>(capabilityType: CapabilityType<T>, provider: CapabilityProvider<T>, options?: CapabilityRegisterOptions): void;
    /** Unregister capability */
    unregister<T>(capabilityType: CapabilityType<T>): boolean;
    /** Check if capability is registered */
    has<T>(capabilityType: CapabilityType<T>): boolean;
    /** Check if capability is ready */
    isReady<T>(capabilityType: CapabilityType<T>): boolean;
    /** Get capability runtime information */
    getInfo<T>(capabilityType: CapabilityType<T>): CapabilityRuntimeInfo | undefined;
    /** Get all registered capability IDs */
    getRegisteredIds(): CapabilityId[];
    /** Get runtime information for all capabilities */
    getAllInfo(): Map<CapabilityId, CapabilityRuntimeInfo>;
    /** Filter capabilities by status */
    getByStatus(status: CapabilityStatus): CapabilityId[];
    /** Filter capabilities by tag */
    getByTag(tag: string): CapabilityId[];
    /** Initialize all registered capabilities */
    initializeAll(): Promise<boolean>;
    /** Dispose all capabilities */
    disposeAll(): Promise<void>;
    /** Reset registry */
    reset(): void;
}
//# sourceMappingURL=capability.d.ts.map