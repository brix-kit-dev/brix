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
 * Shell Types
 *
 * @module @brix-sdk/platform-frame-mobile
 * @since 3.3.0
 */

import type { PluginModule } from './plugins/types';

/**
 * Shell Configuration
 */
export interface ShellConfig {
  /** Application name */
  appName: string;
  /** Application version */
  version: string;
  /** Theme mode */
  theme: 'light' | 'dark' | 'system';
  /** Plugins to load */
  plugins: PluginModule[];
  /** Navigation configuration */
  navigation: {
    /** Navigation type */
    type: 'tab' | 'drawer' | 'stack';
    /** Initial route name */
    initialRoute: string;
  };
  /** API configuration */
  api?: {
    /** Base URL for API requests */
    baseUrl: string;
    /** Request timeout in ms */
    timeout?: number;
  };
  /** Feature flags */
  features?: Record<string, boolean>;
}

/**
 * Shell State
 */
export interface ShellState {
  /** Whether shell has initialized */
  initialized: boolean;
  /** Whether all plugins have loaded */
  pluginsLoaded: boolean;
  /** Current route name */
  currentRoute: string | null;
  /** Error message if any */
  error: string | null;
}
