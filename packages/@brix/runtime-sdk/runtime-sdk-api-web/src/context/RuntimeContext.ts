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
 * @file Runtime Context Abstract Definition
 * @description Defines the core interface for runtime context (no React dependency)
 * @module @brix-sdk/runtime-sdk-api-web/context/RuntimeContext
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Extracted RuntimeContext abstraction from index.ts to keep the contract layer free of React dependencies.
 * React-related Context and Hooks are migrated to @brix-sdk/runtime-sdk-react package.
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
