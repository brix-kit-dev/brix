/**
 * @file useConfig Hook
 * @description Config Capability React Hook
 * @module @brix/runtime-sdk-react/hooks/useConfig
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Migrated from @brix/runtime-sdk-api-web to a standalone React binding package.
 */

import { useMemo, useState, useEffect } from 'react';
import type { ConfigCapability } from '@brix/runtime-sdk-api-web';
import { useRuntimeContext } from './useRuntimeContext';

/**
 * Config Capability Type Identifier
 * @internal
 */
const ConfigCapabilityType = Symbol.for('ConfigCapability');

/**
 * useConfig Hook Return Type
 */
export interface UseConfigResult<T> {
  /** Config value */
  config: T | undefined;
  /** Whether loading */
  isLoading: boolean;
  /** Get specific config item */
  getConfig: <V>(key: string, defaultValue?: V) => V | undefined;
}

/**
 * Get Config Capability Hook
 *
 * <p>Get application config in React components.</p>
 *
 * <h3>Usage Example</h3>
 * ```tsx
 * interface AppConfig {
 *   apiBaseUrl: string;
 *   enableFeatureX: boolean;
 * }
 * 
 * function MyComponent() {
 *   const { config, getConfig } = useConfig<AppConfig>();
 *   
 *   const apiUrl = getConfig('apiBaseUrl', 'https://default.api.com');
 *   // ...
 * }
 * ```
 *
 * @typeParam T - Config type
 * @returns UseConfigResult<T> config and methods
 * @throws Error if used outside RuntimeContextProvider
 * @throws Error if config capability is not registered
 */
export function useConfig<T = Record<string, unknown>>(): UseConfigResult<T> {
  const context = useRuntimeContext();
  const [config, setConfig] = useState<T | undefined>(undefined);
  const [isLoading, setIsLoading] = useState(true);

  const configCapability = useMemo(() => {
    const capability = context.getCapability<ConfigCapability>(ConfigCapabilityType);
    if (!capability) {
      throw new Error(
        '[runtime-sdk-react] ConfigCapability is not registered in RuntimeContext'
      );
    }
    return capability;
  }, [context]);

  // Get full config on initialization
  useEffect(() => {
    let mounted = true;

    const loadConfig = async () => {
      try {
        const allConfig = await configCapability.getAll<T>();
        if (mounted) {
          setConfig(allConfig);
        }
      } catch {
        if (mounted) {
          setConfig(undefined);
        }
      } finally {
        if (mounted) {
          setIsLoading(false);
        }
      }
    };

    loadConfig();

    return () => {
      mounted = false;
    };
  }, [configCapability]);

  const getConfig = useMemo(() => {
    return <V>(key: string, defaultValue?: V): V | undefined => {
      return configCapability.get<V>(key, defaultValue);
    };
  }, [configCapability]);

  return {
    config,
    isLoading,
    getConfig,
  };
}
