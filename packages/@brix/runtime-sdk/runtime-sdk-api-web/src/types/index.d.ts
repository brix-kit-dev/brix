/**
 * @file Type Definition Unified Export
 * @description Re-exports all type definitions from categorized files
 * @module @brix/runtime-sdk-api-web/types
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Split the original 1000+ lines of code from index.ts into the following modules:
 * - capability.ts: Capability system types
 * - plugin.ts: Plugin system types
 * - navigation.ts: Navigation system types
 * - state.ts: State management types
 * - event.ts: Event system types
 * - module.ts: Module system types
 * - http.ts: HTTP client capability types
 * - auth.ts: Authentication capability types
 * - config.ts: Configuration capability types
 * - common.ts: Common utility types and API response types
 *
 * [Design Principles]
 * - Each file has a single responsibility
 * - Enables on-demand imports
 * - Easy to maintain and extend
 */
export * from './capability';
export * from './plugin';
export * from './navigation';
export * from './state';
export * from './event';
export * from './module';
export * from './http';
export * from './auth';
export * from './config';
export * from './common';
//# sourceMappingURL=index.d.ts.map