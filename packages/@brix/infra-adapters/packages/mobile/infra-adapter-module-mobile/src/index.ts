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
 * @file infra-adapter-module-mobile Module Entry
 * @description Brix UI Mobile Module Loading Adapter - React Native dynamic module loading implementation
 * @module @brix-sdk/infra-adapter-module-mobile
 * @version 3.0.0
 * 
 * Module Description:
 * This module is the Mobile module loading adapter layer in the v3.0 Runtime Shell architecture.
 * It is responsible for dynamic loading of plugin modules in React Native environment.
 * 
 * Architecture Position:
 * - This module is an internal dependency of the Mobile Host layer
 * - Plugins should NOT use this module directly
 * - Plugins declare module dependencies through PluginModuleCapability
 * 
 * v3.0 Boundary Constraints:
 * ? Plugins must NOT directly use require()
 * ? Plugins must NOT access Native Module registry
 * ? Plugins must NOT access other modules bypassing the loader
 * ? Plugins declare module dependencies through capability contract
 * ? Module loading is managed by Host
 * 
 * Usage (Host layer only):
 * ```typescript
 * import { RNModuleLoader } from '@brix-sdk/infra-adapter-module-mobile';
 * 
 * const loader = new RNModuleLoader({
 *   registry: moduleRegistry,
 *   cacheEnabled: true,
 * });
 * 
 * const module = await loader.loadModule('booking');
 * ```
 */

export {
  RNModuleLoader,
  type ModuleSource,
  type ModuleMetadata,
  type ModuleRegistry,
  type LoadedModule,
  type RNModuleLoaderOptions,
  type RNScreenComponent,
  type StandardModuleExports,
} from './RNModuleLoader';

// ========== Version Info ==========
export const VERSION = '3.0.0';
