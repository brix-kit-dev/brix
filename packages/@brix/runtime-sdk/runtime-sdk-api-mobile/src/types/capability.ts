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
 * @file Capability related type definitions
 * @description Define core types for the capability system, including capability metadata, status, registry, etc.
 * @module @brix/runtime-sdk-api-mobile/types/capability
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Maintains consistent capability system type definitions with runtime-sdk-api-web.
 *
 * [Design Notes]
 * - All type definitions use readonly properties
 * - Uses Symbol as unique capability identifier (type-safe)
 * - Supports lazy initialization and scope control for capabilities
 */

// =========================================
// Capability Priority
// =========================================

/**
 * Capability Priority Enum
 *
 * <p>Used to determine which implementation to use when multiple capability providers conflict.</p>
 */
export enum CapabilityPriority {
  /** Low priority (fallback implementation) */
  LOW = 0,
  /** Normal priority (default) */
  NORMAL = 50,
  /** High priority (overrides default implementation) */
  HIGH = 100,
}

// =========================================
// Capability Metadata
// =========================================

/**
 * Capability Metadata (simplified version)
 */
export interface CapabilityMetadata {
  /** Capability name */
  readonly name: string;
  /** Capability version */
  readonly version: string;
  /** Priority */
  readonly priority: CapabilityPriority;
  /** Whether required */
  readonly required: boolean;
}

/**
 * Capability ID Type
 *
 * <p>Supports string or Symbol, recommend using Symbol.for() to ensure uniqueness.</p>
 */
export type CapabilityId = string | symbol;

/**
 * Capability Meta Information (full version)
 *
 * <p>Contains complete capability metadata for capability registration and discovery.</p>
 */
export interface CapabilityMeta {
  /** Capability unique identifier */
  readonly id: CapabilityId;

  /** Capability name (human-readable) */
  readonly name: string;

  /** Capability description */
  readonly description?: string;

  /** Capability version */
  readonly version?: string;

  /** List of dependent capability IDs */
  readonly dependencies?: CapabilityId[];

  /** Tags (for categorization and filtering) */
  readonly tags?: string[];
}

// =========================================
// Capability Status
// =========================================

/**
 * Capability Status
 *
 * <p>Describes the current state of a capability in its lifecycle.</p>
 */
export type CapabilityStatus =
  | 'registered'    // Registered, not initialized
  | 'initializing'  // Initializing
  | 'ready'         // Ready to use
  | 'error'         // Error state
  | 'disposed';     // Disposed

// =========================================
// Capability Type Identifier
// =========================================

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
export function createCapabilityType<T>(
  meta: Omit<CapabilityMeta, 'id'> & { id: string }
): CapabilityType<T> {
  return {
    ...meta,
    id: Symbol.for(meta.id),
  } as CapabilityType<T>;
}

// =========================================
// Capability Provider
// =========================================

/**
 * Capability Provider Interface
 *
 * <p>Encapsulates capability instance creation and disposal logic.</p>
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

// =========================================
// Capability Registration Options
// =========================================

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
   * - 'module': Module scope, only available to current module
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

// =========================================
// Capability Runtime Info
// =========================================

/**
 * Capability Runtime Info
 *
 * <p>Describes detailed state of a capability at runtime.</p>
 */
export interface CapabilityRuntimeInfo {
  /** Capability metadata */
  readonly meta: CapabilityMeta;

  /** Current status */
  readonly status: CapabilityStatus;

  /** Registration timestamp */
  readonly registeredAt: number;

  /** Initialization timestamp */
  readonly initializedAt?: number;

  /** Error information (when status is error) */
  readonly error?: Error;

  /** Invocation count statistics */
  readonly invocationCount: number;
}

// =========================================
// Capability Registry Interface
// =========================================

/**
 * Capability Registry Interface
 *
 * <p>Manages capability registration, retrieval, initialization, and disposal.</p>
 */
export interface CapabilityRegistry {
  /** Get capability instance */
  get<T>(capabilityType: CapabilityType<T>): T | undefined;

  /** Get required capability (throws exception if not found) */
  getRequired<T>(capabilityType: CapabilityType<T>): T;

  /** Register capability */
  register<T>(
    capabilityType: CapabilityType<T>,
    provider: CapabilityProvider<T>,
    options?: CapabilityRegisterOptions
  ): void;

  /** Unregister capability */
  unregister<T>(capabilityType: CapabilityType<T>): boolean;

  /** Check if capability is registered */
  has<T>(capabilityType: CapabilityType<T>): boolean;

  /** Check if capability is ready */
  isReady<T>(capabilityType: CapabilityType<T>): boolean;

  /** Get capability runtime info */
  getInfo<T>(capabilityType: CapabilityType<T>): CapabilityRuntimeInfo | undefined;

  /** Get all registered capability IDs */
  getRegisteredIds(): CapabilityId[];

  /** Get runtime info for all capabilities */
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
