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
 * Navigation Hooks - React Hooks for Navigation
 *
 * @module @brix-sdk/platform-frame-mobile/navigation
 * @since 3.3.0
 */

import { useContext } from 'react';
import { NavigationContext, type NavigationContextValue } from './NavigationProvider';

/**
 * useNavigation Hook
 *
 * Provides access to navigation functions within the shell.
 *
 * @returns Navigation context value
 * @throws Error if used outside NavigationProvider
 *
 * @example
 * ```tsx
 * const { navigate, goBack, canGoBack } = useNavigation();
 *
 * const handlePress = () => {
 *   navigate('Details', { id: '123' });
 * };
 * ```
 */
export function useNavigation(): NavigationContextValue {
  const context = useContext(NavigationContext);

  if (!context) {
    throw new Error('useNavigation must be used within a NavigationProvider');
  }

  return context;
}

/**
 * Route Params Hook Return
 */
export interface UseRouteReturn<T = Record<string, unknown>> {
  /** Current route name */
  name: string;
  /** Route parameters */
  params: T;
}

/**
 * useRoute Hook
 *
 * Provides access to current route information and parameters.
 *
 * @returns Current route information
 * @throws Error if used outside NavigationProvider
 *
 * @example
 * ```tsx
 * const { name, params } = useRoute<{ id: string }>();
 * console.log(`Current route: ${name}, ID: ${params.id}`);
 * ```
 */
export function useRoute<T = Record<string, unknown>>(): UseRouteReturn<T> {
  const context = useContext(NavigationContext);

  if (!context) {
    throw new Error('useRoute must be used within a NavigationProvider');
  }

  return {
    name: context.state.currentScreen,
    params: context.state.params as T
  };
}
