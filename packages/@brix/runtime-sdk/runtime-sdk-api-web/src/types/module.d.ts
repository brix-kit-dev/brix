/**
 * @file Module-Related Type Definitions
 * @description Defines core types for the module system, including module metadata, state, lifecycle events, etc.
 * @module @brix/runtime-sdk-api-web/types/module
 * @version 3.2.0
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file.
 */
/**
 * Module Metadata
 *
 * <p>Describes basic information about a module.</p>
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
    /** List of dependent modules */
    readonly dependencies?: string[];
}
/**
 * Module State Enum
 */
export declare enum ModuleState {
    /** Unloaded */
    UNLOADED = "UNLOADED",
    /** Loading */
    LOADING = "LOADING",
    /** Loaded */
    LOADED = "LOADED",
    /** Active */
    ACTIVE = "ACTIVE",
    /** Error state */
    ERROR = "ERROR"
}
/**
 * Module Lifecycle Event Enum
 */
export declare enum ModuleLifecycleEvent {
    /** Before load */
    BEFORE_LOAD = "BEFORE_LOAD",
    /** After load */
    AFTER_LOAD = "AFTER_LOAD",
    /** Before activate */
    BEFORE_ACTIVATE = "BEFORE_ACTIVATE",
    /** After activate */
    AFTER_ACTIVATE = "AFTER_ACTIVATE",
    /** Before deactivate */
    BEFORE_DEACTIVATE = "BEFORE_DEACTIVATE",
    /** After deactivate */
    AFTER_DEACTIVATE = "AFTER_DEACTIVATE",
    /** Error */
    ERROR = "ERROR"
}
//# sourceMappingURL=module.d.ts.map