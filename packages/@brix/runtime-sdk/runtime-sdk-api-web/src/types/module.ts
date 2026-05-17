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
 * @file Module-Related Type Definitions
 * @description Defines core types for the module system, including module metadata, state, lifecycle events, etc.
 * @module @brix-sdk/runtime-sdk-api-web/types/module
 * @version 3.2.0
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file.
 */

// =========================================
// Module Metadata
// =========================================

/**
 * Module Metadata
 *
 * <p>Describes basic information about a module.</p>
 */
export interface ModuleMetadata {
  /** Module ID */
  readonly moduleId: string;
  /** Module Name */
  readonly name: string;
  /** Module Version */
  readonly version: string;
  /** Module Description */
  readonly description?: string;
  /** Author */
  readonly author?: string;
  /** Dependent Module List */
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
  /** Error State */
  ERROR = 'ERROR',
}

// =========================================
// Module Lifecycle Events
// =========================================

/**
 * Module Lifecycle Event Enum
 */
export enum ModuleLifecycleEvent {
  /** Before Load */
  BEFORE_LOAD = 'BEFORE_LOAD',
  /** After Load */
  AFTER_LOAD = 'AFTER_LOAD',
  /** Before Activate */
  BEFORE_ACTIVATE = 'BEFORE_ACTIVATE',
  /** After Activate */
  AFTER_ACTIVATE = 'AFTER_ACTIVATE',
  /** Before Deactivate */
  BEFORE_DEACTIVATE = 'BEFORE_DEACTIVATE',
  /** After Deactivate */
  AFTER_DEACTIVATE = 'AFTER_DEACTIVATE',
  /** Error */
  ERROR = 'ERROR',
}
