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
 * @file PluginLoaderCapabilityImpl — Formal PluginLoader Capability Wrapper
 * @description Wraps RemoteComponent and preloadContainer from infra-adapter-mf-web
 *              into a formal Capability class implementing PluginLoaderCapability.
 * @module @brix-sdk/infra-adapter-mf-web/PluginLoaderCapabilityImpl
 * @version 3.2.1
 *
 * [Architecture Positioning]
 * Infra Adapter layer — bridges Module Federation loading mechanism to the
 * PluginLoaderCapability contract defined in runtime-sdk-api-web.
 *
 * [Architecture Compliance]
 * - Blueprint v3.0.9: All four Capability chains must have formal Impl classes
 * - Phase 2.4: Formal PluginLoaderCapabilityImpl wrapping infra-adapter-mf-web
 *
 * [Migration Guide]
 * Before (anonymous inline object in bootstrap.tsx):
 *   runtime.registerCapability(PluginLoaderCapabilityType, {
 *     provide: () => ({ RemoteComponent, preload: ... }),
 *   });
 *
 * After (formal Impl class):
 *   const pluginLoader = new PluginLoaderCapabilityImpl();
 *   runtime.registerCapability(PluginLoaderCapabilityType, {
 *     provide: () => pluginLoader,
 *   });
 *
 * @since 3.2.1
 * @see PluginLoaderCapability — Contract in runtime-sdk-api-web
 * @see RemoteComponent — MF adapter component in this package
 */

import type { ComponentType } from 'react';
import type {
  PluginLoaderCapability,
  RemoteComponentProps,
} from '@brix-sdk/runtime-sdk-api-web';
import { RemoteComponent } from './RemoteComponent';
import { preloadContainer } from './mf-loader';

/**
 * Configuration for PluginLoaderCapabilityImpl.
 */
export interface PluginLoaderCapabilityConfig {
  /**
   * Custom RemoteComponent implementation.
   * Defaults to the built-in MF RemoteComponent.
   */
  remoteComponent?: ComponentType<RemoteComponentProps>;

  /**
   * Custom preload function.
   * Defaults to preloadContainer from mf-loader.
   */
  preloadFn?: (remoteEntries: string[]) => Promise<void>;
}

/**
 * Formal PluginLoaderCapability implementation backed by Module Federation.
 *
 * Wraps RemoteComponent and preloadContainer into a class that follows
 * the standard CapabilityImpl pattern used across the Brix platform.
 *
 * @example
 * ```typescript
 * import { PluginLoaderCapabilityImpl } from '@brix-sdk/infra-adapter-mf-web';
 *
 * // Default: uses built-in RemoteComponent + preloadContainer
 * const pluginLoader = new PluginLoaderCapabilityImpl();
 *
 * runtime.registerCapability(PluginLoaderCapabilityType, {
 *   provide: () => pluginLoader,
 * });
 * ```
 */
export class PluginLoaderCapabilityImpl implements PluginLoaderCapability {
  readonly RemoteComponent: ComponentType<RemoteComponentProps>;
  private readonly preloadFn: (remoteEntries: string[]) => Promise<void>;

  constructor(config?: PluginLoaderCapabilityConfig) {
    this.RemoteComponent = config?.remoteComponent ?? RemoteComponent;
    this.preloadFn = config?.preloadFn ?? defaultPreload;
  }

  /**
   * Preload remote entry scripts for faster subsequent loading.
   *
   * @param remoteEntries - Array of remote entry URLs to preload
   */
  async preload(remoteEntries: string[]): Promise<void> {
    await this.preloadFn(remoteEntries);
  }
}

/**
 * Default preload implementation using preloadContainer from mf-loader.
 * @internal
 */
async function defaultPreload(remoteEntries: string[]): Promise<void> {
  await Promise.all(remoteEntries.map((entry) => preloadContainer(entry)));
}
