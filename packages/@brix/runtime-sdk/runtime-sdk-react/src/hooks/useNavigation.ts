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
