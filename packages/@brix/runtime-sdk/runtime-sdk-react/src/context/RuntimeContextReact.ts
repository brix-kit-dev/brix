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
 * @file React Runtime Context
 * @description Provides React Context wrapper for runtime context
 * @module @brix/runtime-sdk-react/context/RuntimeContextReact
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Migrated from @brix/runtime-sdk-api-web to a standalone React binding package.
 * Keeps the contract layer (runtime-sdk-api-web) free of React dependencies.
 *
 * [Design Principles]
 * - Contains only React-specific binding code
 * - Depends on pure type definitions from @brix/runtime-sdk-api-web
 */

import { createContext, type Context } from 'react';
import type { RuntimeContext } from '@brix/runtime-sdk-api-web';

/**
 * Window-global key for the singleton RuntimeContext.
 *
 * In Module Federation architectures, Host and Remote plugins may each bundle
 * their own copy of this module. Using a window-global singleton ensures that
 * all copies share the SAME React Context object, which is critical for
 * useContext() to find the Provider value set by the Host.
 *
 * @see https://module-federation.io/guide/troubleshooting/runtime/RUNTIME-006
 */
const GLOBAL_KEY = '__BRIX_RUNTIME_CONTEXT__';

/**
 * Get or create the singleton RuntimeContext React Context.
 *
 * <p>Uses a window-global to guarantee a single Context object identity
 * across multiple copies of this module loaded in a Module Federation setup.</p>
 */
function getOrCreateContext(): Context<RuntimeContext | null> {
  // Use globalThis for universal compatibility (browser, Node, SSR)
  const g = globalThis as Record<string, unknown>;
  if (!g[GLOBAL_KEY]) {
    g[GLOBAL_KEY] = createContext<RuntimeContext | null>(null);
  }
  return g[GLOBAL_KEY] as Context<RuntimeContext | null>;
}

/**
 * RuntimeContext React Context
 *
 * <p>React Context instance for passing runtime context through the component tree.</p>
 * <p><strong>Module Federation Safe:</strong> Uses window-global singleton to ensure
 * Host and Remote plugins always share the same Context identity.</p>
 *
 * <h3>Usage Example</h3>
 * ```tsx
 * // Shell layer provides Context
 * <RuntimeContextReact.Provider value={runtimeContext}>
 *   <App />
 * </RuntimeContextReact.Provider>
 *
 * // Plugin component consumes Context
 * const context = useContext(RuntimeContextReact);
 * ```
 */
export const RuntimeContextReact = getOrCreateContext();

/**
 * RuntimeContext Provider Component
 *
 * <p>Convenient Provider component with default value checking support.</p>
 */
export const RuntimeContextProvider = RuntimeContextReact.Provider;
