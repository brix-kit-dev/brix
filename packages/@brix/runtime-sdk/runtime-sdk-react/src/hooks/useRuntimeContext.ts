/**
 * @file useRuntimeContext Hook
 * @description React Hook for getting runtime context
 * @module @brix/runtime-sdk-react/hooks/useRuntimeContext
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Migrated from @brix/runtime-sdk-api-web to a standalone React binding package.
 */

import { useContext } from 'react';
import type { RuntimeContext } from '@brix/runtime-sdk-api-web';
import { RuntimeContextReact } from '../context/RuntimeContextReact';

/**
 * Get Runtime Context Hook
 *
 * <p>Get runtime context instance in React components.</p>
 *
 * <h3>Usage Example</h3>
 * ```tsx
 * function MyComponent() {
 *   const context = useRuntimeContext();
 *   const http = context.getCapability<HttpCapability>(HttpCapabilityType);
 *   // ...
 * }
 * ```
 *
 * @returns RuntimeContext instance
 * @throws Error if used outside RuntimeContextProvider
 */
export function useRuntimeContext(): RuntimeContext {
  const context = useContext(RuntimeContextReact);
  if (!context) {
    throw new Error(
      '[runtime-sdk-react] useRuntimeContext must be used within RuntimeContextProvider'
    );
  }
  return context;
}
