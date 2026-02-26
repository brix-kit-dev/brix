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

import { createContext } from 'react';
import type { RuntimeContext } from '@brix/runtime-sdk-api-web';

/**
 * RuntimeContext React Context
 *
 * <p>React Context instance for passing runtime context through the component tree.</p>
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
export const RuntimeContextReact = createContext<RuntimeContext | null>(null);

/**
 * RuntimeContext Provider Component
 *
 * <p>Convenient Provider component with default value checking support.</p>
 */
export const RuntimeContextProvider = RuntimeContextReact.Provider;
