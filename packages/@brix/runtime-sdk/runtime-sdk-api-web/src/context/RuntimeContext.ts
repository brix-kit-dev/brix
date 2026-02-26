/**
 * @file Runtime Context Abstract Definition
 * @description Defines the core interface for runtime context (no React dependency)
 * @module @brix/runtime-sdk-api-web/context/RuntimeContext
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Extracted RuntimeContext abstraction from index.ts to keep the contract layer free of React dependencies.
 * React-related Context and Hooks are migrated to @brix/runtime-sdk-react package.
 *
 * [Design Principles]
 * - Pure abstract interface, no dependency on any UI framework
 * - Can be used in React, Vue, native JS, and other environments
 */

// =========================================
// Runtime Context Interface
// =========================================

/**
 * Runtime Context Interface
 *
 * <p>Provides a unified entry point for plugins to access runtime capabilities.</p>
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Provides module ID identification</li>
 *   <li>Provides tenant ID identification</li>
 *   <li>Provides capability retrieval method</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const http = context.getCapability<HttpCapability>(HttpCapabilityType);
 * const nav = context.getCapability<NavigationCapability>(NavigationCapabilityType);
 * ```
 */
export interface RuntimeContext {
  /**
   * Module/Plugin ID
   *
   * <p>Unique identifier for the current plugin.</p>
   */
  readonly moduleId: string;

  /**
   * Tenant ID
   *
   * <p>Tenant identifier for the current runtime environment.</p>
   */
  readonly tenantId: string;

  /**
   * Get capability instance
   *
   * @param capabilityType Capability type identifier (Symbol)
   * @returns Capability instance, returns undefined if not found
   */
  getCapability<T>(capabilityType: symbol): T | undefined;
}
