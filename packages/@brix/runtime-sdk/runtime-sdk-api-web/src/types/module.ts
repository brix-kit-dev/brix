/**
 * @file Module-Related Type Definitions
 * @description Defines core types for the module system, including module metadata, state, lifecycle events, etc.
 * @module @brix/runtime-sdk-api-web/types/module
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
