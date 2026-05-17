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
 * useShell Hook
 *
 * Provides access to the Shell context for configuration
 * and state management within the mobile shell.
 *
 * @module @brix-sdk/platform-frame-mobile/hooks
 * @since 3.3.0
 */

import { useContext } from 'react';
import { ShellContext } from '../providers/ShellProvider';
import type { ShellConfig, ShellState } from '../types';

/**
 * Shell Hook Return Type
 */
export interface UseShellReturn {
  /** Shell configuration */
  config: ShellConfig;
  /** Shell runtime state */
  state: ShellState;
  /** Whether the shell is ready */
  isReady: boolean;
}

/**
 * Hook to access the shell context.
 *
 * Must be used within a ShellProvider.
 *
 * @returns Shell configuration and state
 * @throws Error if used outside ShellProvider
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { config, state, isReady } = useShell();
 *   return <Text>{config.appName} - {isReady ? 'Ready' : 'Loading'}</Text>;
 * }
 * ```
 */
export function useShell(): UseShellReturn {
  const context = useContext(ShellContext);
  if (!context) {
    throw new Error('useShell must be used within a ShellProvider');
  }
  return {
    config: context.config,
    state: context.state,
    isReady: context.isReady
  };
}
