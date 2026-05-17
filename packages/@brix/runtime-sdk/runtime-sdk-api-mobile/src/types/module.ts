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
 * @file Module related type definitions
 * @description Define core types for the module system, including module metadata, state, lifecycle events, etc.
 * @module @brix-sdk/runtime-sdk-api-mobile/types/module
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Maintains consistent module system type definitions with runtime-sdk-api-web.
 */

// =========================================
// Module Metadata
// =========================================

/**
 * Module Metadata
 *
 * <p>Describes basic module information.</p>
 */
export interface ModuleMetadata {
  /** Module ID */
  readonly moduleId: string;
  /** Module name */
  readonly name: string;
  /** Module version */
  readonly version: string;
  /** Module description */
  readonly description?: string;
  /** Author */
  readonly author?: string;
  /** Dependent module list */
  readonly dependencies?: string[];
}

// =========================================
// Module State
// =========================================

/**
 * Module State Enum
 */
export enum ModuleState {
  /** Unloaded */
  UNLOADED = 'UNLOADED',
  /** Loading */
  LOADING = 'LOADING',
  /** Loaded */
  LOADED = 'LOADED',
  /** Active */
  ACTIVE = 'ACTIVE',
  /** Error state */
  ERROR = 'ERROR',
}

// =========================================
// Module Lifecycle Events
// =========================================

/**
 * Module Lifecycle Event Enum
 */
export enum ModuleLifecycleEvent {
  /** Before load */
  BEFORE_LOAD = 'BEFORE_LOAD',
  /** After load */
  AFTER_LOAD = 'AFTER_LOAD',
  /** Before activate */
  BEFORE_ACTIVATE = 'BEFORE_ACTIVATE',
  /** After activate */
  AFTER_ACTIVATE = 'AFTER_ACTIVATE',
  /** Before deactivate */
  BEFORE_DEACTIVATE = 'BEFORE_DEACTIVATE',
  /** After deactivate */
  AFTER_DEACTIVATE = 'AFTER_DEACTIVATE',
  /** Error */
  ERROR = 'ERROR',
}
