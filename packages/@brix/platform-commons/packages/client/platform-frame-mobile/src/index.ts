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
 * @file Platform Frame Mobile Module Entry
 * @description Mobile platform frame package - Provides shell infrastructure,
 * navigation, plugin loading, and layout capabilities for mobile apps
 * @module @brix-sdk/platform-frame-mobile
 * @version 3.3.0
 *
 * [Module Description]
 * platform-frame-mobile is the Mobile platform frame package (Layer 2C), providing:
 * - Shell provider and navigation
 * - Plugin loading and registry
 * - Layout components (AppLayout, TabNavigator, DrawerNavigator)
 * - Default screens (HomeScreen, SettingsScreen)
 * - Deep linking support
 *
 * [Architecture Position]
 * ```text
 * +-------------------------------------------------------------------------+
 * | Capability Layer (platform-commons/client)                              |
 * | +-- platform-frame-web     - Web frame + Layout + Theme capabilities    |
 * | +-- platform-frame-mobile  - Mobile frame + Navigation + Plugin loading |
 * |      +-- providers/        - ShellProvider                              |
 * |      +-- navigation/       - ShellNavigator, NavigationProvider         |
 * |      +-- plugins/          - PluginLoader, PluginRegistry              |
 * |      +-- layouts/          - AppLayout, TabNavigator, DrawerNavigator  |
 * |      +-- screens/          - HomeScreen, SettingsScreen                |
 * |      +-- hooks/            - useShell, usePlugins, useDeepLinking      |
 * +-------------------------------------------------------------------------+
 * ```
 */

// Core Shell Components
export { ShellProvider } from './providers/ShellProvider';
export { ShellNavigator } from './navigation/ShellNavigator';

// Plugin Management
export { PluginLoader } from './plugins/PluginLoader';
export { PluginRegistry } from './plugins/PluginRegistry';
export type { PluginModule, PluginManifest, PluginLoadOptions } from './plugins/types';

// Navigation
export { NavigationProvider } from './navigation/NavigationProvider';
export { useNavigation, useRoute } from './navigation/hooks';
export type { NavigationState, RouteConfig, NavigationOptions } from './navigation/types';

// Layout Components
export { AppLayout } from './layouts/AppLayout';
export { TabNavigator } from './layouts/TabNavigator';
export { DrawerNavigator } from './layouts/DrawerNavigator';

// Screen Components
export * from './screens';

// Hooks
export { useShell } from './hooks/useShell';
export { usePlugins } from './hooks/usePlugins';
export { useDeepLinking } from './hooks/useDeepLinking';

// Types
export type { ShellConfig, ShellState } from './types';
