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
 * @file useNavigation Hook
 * @description Navigation Capability React Hook
 * @module @brix/runtime-sdk-react/hooks/useNavigation
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Migrated from @brix/runtime-sdk-api-web to a standalone React binding package.
 */

import { useMemo } from 'react';
import type { NavigationCapability } from '@brix/runtime-sdk-api-web';
import { useRuntimeContext } from './useRuntimeContext';

/**
 * Navigation Capability Type Identifier
 * @internal
 */
const NavigationCapabilityType = Symbol.for('NavigationCapability');

/**
 * Get Navigation Capability Hook
 *
 * <p>Get navigation capability instance in React components.</p>
 *
 * <h3>Usage Example</h3>
 * ```tsx
 * function MyComponent() {
 *   const navigation = useNavigation();
 *   
 *   const handleClick = () => {
 *     navigation.navigate('/dashboard', { replace: true });
 *   };
 *   // ...
 * }
 * ```
 *
 * @returns NavigationCapability instance
 * @throws Error if used outside RuntimeContextProvider
 * @throws Error if navigation capability is not registered
 */
export function useNavigation(): NavigationCapability {
  const context = useRuntimeContext();

  return useMemo(() => {
    const capability = context.getCapability<NavigationCapability>(NavigationCapabilityType);
    if (!capability) {
      throw new Error(
        '[runtime-sdk-react] NavigationCapability is not registered in RuntimeContext'
      );
    }
    return capability;
  }, [context]);
}
