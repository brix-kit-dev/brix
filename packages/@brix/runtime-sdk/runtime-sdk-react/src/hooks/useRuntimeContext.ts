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
 * @file useRuntimeContext Hook
 * @description React Hook for getting runtime context
 * @module @brix-sdk/runtime-sdk-react/hooks/useRuntimeContext
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Migrated from @brix-sdk/runtime-sdk-api-web to a standalone React binding package.
 */

import { useContext } from 'react';
import type { RuntimeContext } from '@brix-sdk/runtime-sdk-api-web';
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
