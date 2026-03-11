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
 * @file usePluginLoader Hook
 * @description Hook to access PluginLoaderCapability for remote component rendering
 * @module @brix/runtime-sdk-react/hooks/usePluginLoader
 * @version 3.2.0
 *
 * [v3.2.0 Added - D6 Fix]
 * Provides access to PluginLoaderCapability for rendering remote plugin components.
 * Shell components should use this hook instead of directly importing infra-adapter-mf-web.
 *
 * [Usage Example]
 * ```tsx
 * function PluginContainer({ route }) {
 *   const { RemoteComponent } = usePluginLoader();
 *
 *   return (
 *     <RemoteComponent
 *       remoteEntry={route.plugin.remoteEntry}
 *       exposePath="./Component"
 *       fallback={<Loading />}
 *     />
 *   );
 * }
 * ```
 */

import { useContext } from 'react';
import { RuntimeContextReact } from '../context/RuntimeContextReact';
import type { PluginLoaderCapability } from '@brix/runtime-sdk-api-web';
import { PluginLoaderCapabilityType } from '@brix/runtime-sdk-api-web';

/**
 * Hook Result Type - alias for PluginLoaderCapability
 */
export type UsePluginLoaderResult = PluginLoaderCapability;

/**
 * Hook to get PluginLoaderCapability
 *
 * <p>The PluginLoaderCapability provides:</p>
 * <ul>
 *   <li>RemoteComponent: For rendering remote plugin components</li>
 *   <li>preload: For preloading remote entry scripts (optional)</li>
 *   <li>isModuleAvailable: For checking module availability (optional)</li>
 * </ul>
 *
 * <p>Host must register the capability via capabilityRegistry.</p>
 *
 * @returns PluginLoaderCapability instance
 * @throws Error if capability not registered
 *
 * @example
 * ```tsx
 * function DynamicPluginRoute({ route }) {
 *   const { RemoteComponent } = usePluginLoader();
 *
 *   return (
 *     <RemoteComponent
 *       remoteEntry={route.plugin.remoteEntry}
 *       exposePath={route.exposePath}
 *       fallback={<div>Loading {route.title}...</div>}
 *     />
 *   );
 * }
 * ```
 */
export function usePluginLoader(): UsePluginLoaderResult {
  const context = useContext(RuntimeContextReact);

  if (!context) {
    throw new Error(
      '[runtime-sdk-react] usePluginLoader must be used within RuntimeContextProvider.'
    );
  }

  const capability = context.getCapability<PluginLoaderCapability>(
    PluginLoaderCapabilityType
  );

  if (!capability) {
    throw new Error(
      '[runtime-sdk-react] PluginLoaderCapability not registered. ' +
      'Ensure Host has registered the plugin loader adapter via capabilityRegistry.register().'
    );
  }

  return capability;
}

/**
 * Hook to get PluginLoaderCapability (optional version)
 *
 * <p>Same as usePluginLoader but returns undefined instead of throwing if not registered.</p>
 *
 * @returns PluginLoaderCapability instance or undefined
 *
 * @example
 * ```tsx
 * function OptionalPluginLoader() {
 *   const pluginLoader = usePluginLoaderOptional();
 *
 *   if (!pluginLoader) {
 *     return <div>Plugin loading not available</div>;
 *   }
 *
 *   const { RemoteComponent } = pluginLoader;
 *   // ...
 * }
 * ```
 */
export function usePluginLoaderOptional(): UsePluginLoaderResult | undefined {
  const context = useContext(RuntimeContextReact);
  
  if (!context) {
    return undefined;
  }

  return context.getCapability<PluginLoaderCapability>(
    PluginLoaderCapabilityType
  );
}
