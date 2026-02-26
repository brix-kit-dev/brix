/**
 * @file usePluginState Hook
 * @description Plugin State Management Capability React Hook
 * @module @brix/runtime-sdk-react/hooks/usePluginState
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Migrated from @brix/runtime-sdk-api-web to a standalone React binding package.
 */

import { useMemo, useState, useEffect, useCallback } from 'react';
import type { PluginStateCapability } from '@brix/runtime-sdk-api-web';
import { useRuntimeContext } from './useRuntimeContext';

/**
 * Plugin State Capability Type Identifier
 * @internal
 */
const PluginStateCapabilityType = Symbol.for('PluginStateCapability');

/**
 * usePluginState Hook Return Type
 */
export interface UsePluginStateResult<T> {
  /** Current state value */
  state: T | undefined;
  /** Set state */
  setState: (value: T) => void;
  /** Reset state to initial value */
  resetState: () => void;
  /** Whether loading */
  isLoading: boolean;
}

/**
 * Get Plugin State Management Capability Hook
 *
 * <p>Get and manage plugin state in React components, with automatic state change subscription.</p>
 *
 * <h3>Usage Example</h3>
 * ```tsx
 * interface UserPreferences {
 *   theme: 'light' | 'dark';
 *   language: string;
 * }
 * 
 * function MyComponent() {
 *   const { state, setState } = usePluginState<UserPreferences>('user-preferences');
 *   
 *   const toggleTheme = () => {
 *     setState({
 *       ...state,
 *       theme: state?.theme === 'light' ? 'dark' : 'light'
 *     });
 *   };
 *   // ...
 * }
 * ```
 *
 * @typeParam T - State type
 * @param key - State key name
 * @param initialValue - Initial value (optional)
 * @returns UsePluginStateResult<T> state and operation methods
 * @throws Error if used outside RuntimeContextProvider
 * @throws Error if plugin state capability is not registered
 */
export function usePluginState<T>(
  key: string, 
  initialValue?: T
): UsePluginStateResult<T> {
  const context = useRuntimeContext();
  const [state, setLocalState] = useState<T | undefined>(initialValue);
  const [isLoading, setIsLoading] = useState(true);

  const stateCapability = useMemo(() => {
    const capability = context.getCapability<PluginStateCapability>(PluginStateCapabilityType);
    if (!capability) {
      throw new Error(
        '[runtime-sdk-react] PluginStateCapability is not registered in RuntimeContext'
      );
    }
    return capability;
  }, [context]);

  // Get state on initialization
  useEffect(() => {
    let mounted = true;

    const loadState = async () => {
      try {
        const currentState = stateCapability.get<T>(key);
        if (mounted) {
          setLocalState(currentState ?? initialValue);
        }
      } catch {
        if (mounted) {
          setLocalState(initialValue);
        }
      } finally {
        if (mounted) {
          setIsLoading(false);
        }
      }
    };

    loadState();

    // Subscribe to state changes
    const unsubscribe = stateCapability.subscribe<T>(key, (newState) => {
      if (mounted) {
        setLocalState(newState);
      }
    });

    return () => {
      mounted = false;
      unsubscribe();
    };
  }, [stateCapability, key, initialValue]);

  const setState = useCallback((value: T) => {
    stateCapability.set(key, value);
    setLocalState(value);
  }, [stateCapability, key]);

  const resetState = useCallback(() => {
    stateCapability.set(key, initialValue);
    setLocalState(initialValue);
  }, [stateCapability, key, initialValue]);

  return {
    state,
    setState,
    resetState,
    isLoading,
  };
}
