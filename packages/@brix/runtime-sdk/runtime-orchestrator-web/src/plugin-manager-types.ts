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
 * @file Plugin Manager Types
 * @description Type definitions for PluginManager
 * @module @brix/runtime-orchestrator-web/plugin-manager-types
 * @version 3.0.0
 * 
 * Extracted from PluginManager.ts as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.
 * 
 * 【中文技术要点】
 * 插件管理器的类型定义，包括插件运行时状态、贡献项和配置。
 */

import type {
  PluginEntry,
  PluginLifecycle,
  PluginStatus,
} from '@brix/runtime-sdk-api-web';

/**
 * Plugin runtime information
 * 
 * Stores plugin registration information and runtime state.
 * 
 * 【插件运行时信息】存储插件注册信息和运行时状态。
 */
export interface PluginRuntime {
  /** Plugin entry configuration */
  entry: PluginEntry;
  
  /** Plugin instance (after loading) */
  instance?: PluginLifecycle;
  
  /** Plugin status */
  status: PluginStatus;
  
  /** Load timestamp */
  loadedAt?: number;
  
  /** Activation timestamp */
  activatedAt?: number;
  
  /** Error information */
  error?: Error;
  
  /** Resources contributed by plugin */
  contributions: PluginContribution[];
}

/**
 * Plugin contribution
 * 
 * Records resources contributed by plugin to the system (routes, menus, capabilities, etc.)
 * 
 * 【插件贡献项】记录插件向系统贡献的资源（路由、菜单、能力等）。
 */
export interface PluginContribution {
  /** Contribution type */
  type: 'route' | 'menu' | 'capability' | 'eventHandler' | 'other';
  
  /** Contribution identifier */
  id: string;
  
  /** Cleanup function */
  cleanup?: () => void;
}

/**
 * Plugin manager configuration
 * 
 * 【插件管理器配置】控制插件加载和激活行为。
 */
export interface PluginManagerConfig {
  /** Enable strict mode (stop immediately on error) */
  strictMode?: boolean;
  
  /** Plugin load timeout (milliseconds) */
  loadTimeout?: number;
  
  /** Plugin activation timeout (milliseconds) */
  activateTimeout?: number;
  
  /** Enable hot reload support */
  hotReload?: boolean;
}

/**
 * Default configuration
 */
export const DEFAULT_CONFIG: Required<PluginManagerConfig> = {
  strictMode: false,
  loadTimeout: 30000,
  activateTimeout: 10000,
  hotReload: false,
};
