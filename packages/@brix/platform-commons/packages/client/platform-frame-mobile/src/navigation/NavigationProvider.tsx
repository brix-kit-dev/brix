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
 * Navigation Provider - Navigation Context for Mobile Shell
 *
 * This component provides navigation context and utilities
 * for plugin screens within the shell.
 *
 * @module @brix-sdk/platform-frame-mobile/navigation
 * @since 3.3.0
 */

import { createContext, useState, useCallback, useMemo } from 'react';
import type { ReactNode } from 'react';
import type { NavigationState, RouteConfig } from './types';

/**
 * Navigation Context Value
 */
export interface NavigationContextValue {
  /** Current navigation state */
  state: NavigationState;
  /** Navigate to a screen */
  navigate: (screen: string, params?: Record<string, unknown>) => void;
  /** Go back to previous screen */
  goBack: () => void;
  /** Reset navigation state */
  reset: (routes: RouteConfig[]) => void;
  /** Push a new screen onto the stack */
  push: (screen: string, params?: Record<string, unknown>) => void;
  /** Pop the current screen from the stack */
  pop: () => void;
  /** Check if can go back */
  canGoBack: () => boolean;
}

/**
 * Navigation Context
 */
export const NavigationContext = createContext<NavigationContextValue | null>(null);

/**
 * Navigation Provider Props
 */
export interface NavigationProviderProps {
  /** Child components */
  children: ReactNode;
  /** Initial routes */
  initialRoutes?: RouteConfig[];
  /** Initial route name */
  initialRouteName?: string;
}

/**
 * NavigationProvider Component
 *
 * Provides navigation context to child components. This is typically
 * used internally by ShellProvider but can be used standalone.
 *
 * @example
 * ```tsx
 * <NavigationProvider initialRouteName="Home">
 *   <App />
 * </NavigationProvider>
 * ```
 */
export function NavigationProvider({
  children,
  initialRoutes = [],
  initialRouteName = 'Home'
}: NavigationProviderProps): JSX.Element {
  // Navigation state
  const [state, setState] = useState<NavigationState>({
    currentScreen: initialRouteName,
    params: {},
    history: [initialRouteName],
    routes: initialRoutes
  });

  // Navigation actions
  const navigate = useCallback((screen: string, params?: Record<string, unknown>) => {
    setState(prev => ({
      ...prev,
      currentScreen: screen,
      params: params ?? {},
      history: [...prev.history, screen]
    }));
  }, []);

  const goBack = useCallback(() => {
    setState(prev => {
      if (prev.history.length <= 1) return prev;
      const newHistory = prev.history.slice(0, -1);
      return {
        ...prev,
        currentScreen: newHistory[newHistory.length - 1],
        params: {},
        history: newHistory
      };
    });
  }, []);

  const reset = useCallback((routes: RouteConfig[]) => {
    setState(prev => ({
      ...prev,
      routes,
      currentScreen: routes[0]?.name ?? 'Home',
      params: {},
      history: [routes[0]?.name ?? 'Home']
    }));
  }, []);

  const push = useCallback((screen: string, params?: Record<string, unknown>) => {
    navigate(screen, params);
  }, [navigate]);

  const pop = useCallback(() => {
    goBack();
  }, [goBack]);

  const canGoBack = useCallback(() => {
    return state.history.length > 1;
  }, [state.history]);

  // Context value
  const contextValue = useMemo<NavigationContextValue>(
    () => ({
      state,
      navigate,
      goBack,
      reset,
      push,
      pop,
      canGoBack
    }),
    [state, navigate, goBack, reset, push, pop, canGoBack]
  );

  return (
    <NavigationContext.Provider value={contextValue}>
      {children}
    </NavigationContext.Provider>
  );
}
