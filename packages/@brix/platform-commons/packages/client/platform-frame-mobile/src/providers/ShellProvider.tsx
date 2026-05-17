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
 * Shell Provider - Root Context Provider for Mobile Shell
 *
 * This component wraps the entire application and provides all necessary
 * context providers for capabilities, navigation, and state management.
 *
 * @module @brix-sdk/platform-frame-mobile/providers
 * @since 3.3.0
 */

import React, { useMemo, useCallback, useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import type { ShellConfig, ShellState } from '../types';

/**
 * Shell Context Value
 */
export interface ShellContextValue {
  /** Current shell state */
  state: ShellState;
  /** Shell configuration */
  config: ShellConfig;
  /** Update shell state */
  setState: (state: Partial<ShellState>) => void;
  /** Check if shell is ready */
  isReady: boolean;
}

/**
 * Default shell state
 */
const DEFAULT_STATE: ShellState = {
  initialized: false,
  pluginsLoaded: false,
  currentRoute: null,
  error: null
};

/**
 * Default shell config
 */
const DEFAULT_CONFIG: ShellConfig = {
  appName: 'Brix Platform',
  version: '3.3.0',
  theme: 'light',
  plugins: [],
  navigation: {
    type: 'tab',
    initialRoute: 'Home'
  }
};

/**
 * Shell Context
 */
export const ShellContext = React.createContext<ShellContextValue | null>(null);

/**
 * Shell Provider Props
 */
export interface ShellProviderProps {
  /** Child components */
  children: ReactNode;
  /** Shell configuration */
  config?: Partial<ShellConfig>;
  /** Callback when shell is ready */
  onReady?: () => void;
  /** Callback when shell encounters an error */
  onError?: (error: Error) => void;
}

/**
 * ShellProvider Component
 *
 * Root provider component that initializes and manages the mobile shell.
 * Wraps children with all necessary context providers.
 *
 * @example
 * ```tsx
 * import { ShellProvider } from '@brix-sdk/platform-frame-mobile';
 *
 * function App() {
 *   return (
 *     <ShellProvider
 *       config={{
 *         appName: 'My App',
 *         plugins: [BookingPlugin, IdentityPlugin]
 *       }}
 *       onReady={() => console.log('Shell ready!')}
 *     >
 *       <ShellNavigator />
 *     </ShellProvider>
 *   );
 * }
 * ```
 */
export function ShellProvider({
  children,
  config: userConfig,
  onReady,
  onError
}: ShellProviderProps): JSX.Element {
  // Merge user config with defaults
  const config = useMemo<ShellConfig>(
    () => ({ ...DEFAULT_CONFIG, ...userConfig }),
    [userConfig]
  );

  // Shell state management
  const [state, setStateInternal] = useState<ShellState>(DEFAULT_STATE);

  // State updater function
  const setState = useCallback((partial: Partial<ShellState>) => {
    setStateInternal(prev => ({ ...prev, ...partial }));
  }, []);

  // Initialize shell
  useEffect(() => {
    const initializeShell = async () => {
      try {
        // Mark as initialized
        setState({ initialized: true });

        // Notify ready callback
        onReady?.();
      } catch (error) {
        const err = error instanceof Error ? error : new Error('Shell initialization failed');
        setState({ error: err.message });
        onError?.(err);
      }
    };

    initializeShell();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Context value
  const contextValue = useMemo<ShellContextValue>(
    () => ({
      state,
      config,
      setState,
      isReady: state.initialized && state.pluginsLoaded
    }),
    [state, config, setState]
  );

  return (
    <ShellContext.Provider value={contextValue}>
      {children}
    </ShellContext.Provider>
  );
}
