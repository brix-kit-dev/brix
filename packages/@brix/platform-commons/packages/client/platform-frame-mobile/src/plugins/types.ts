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
 * Plugin Types
 *
 * @module @brix-sdk/platform-frame-mobile/plugins
 * @since 3.3.0
 */

import type { RouteConfig } from '../navigation/types';
import type { PluginRegistry } from './PluginRegistry';

/**
 * Plugin Manifest
 *
 * Describes the plugin's metadata, capabilities, and requirements.
 */
export interface PluginManifest {
  /** Unique plugin identifier */
  id: string;
  /** Plugin display name */
  name: string;
  /** Plugin version */
  version: string;
  /** Plugin description */
  description?: string;
  /** Plugin author */
  author?: string;
  /** Plugin dependencies (other plugin IDs) */
  dependencies?: string[];
  /** Required capabilities */
  capabilities?: {
    required?: string[];
    optional?: string[];
  };
  /** Plugin icon name */
  icon?: string;
  /** Priority for loading order */
  priority?: number;
}

/**
 * Plugin Module
 *
 * The main plugin module interface that all plugins must implement.
 */
export interface PluginModule {
  /** Plugin manifest */
  manifest: PluginManifest;
  /** Routes provided by this plugin */
  routes?: RouteConfig[];
  /** Initialize the plugin */
  initialize?: (context: PluginInitContext) => Promise<void> | void;
  /** Destroy the plugin (cleanup) */
  destroy?: () => void;
}

/**
 * Plugin Initialization Context
 */
export interface PluginInitContext {
  /** Plugin registry for inter-plugin communication */
  registry: PluginRegistry;
  /** Plugin-specific configuration */
  config: Record<string, unknown>;
}

/**
 * Plugin Load Options
 */
export interface PluginLoadOptions {
  /** Whether to fail on plugin load error */
  failOnError?: boolean;
  /** Plugin-specific configuration */
  pluginConfig?: Record<string, unknown>;
  /** Timeout for plugin loading (ms) */
  timeout?: number;
}

/**
 * Plugin Registry Entry
 */
export interface PluginRegistryEntry {
  /** Plugin manifest */
  manifest: PluginManifest;
  /** Plugin module */
  module: PluginModule;
  /** When the plugin was loaded */
  loadedAt: Date;
}
