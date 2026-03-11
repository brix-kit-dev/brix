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
 * @file @brix/runtime-sdk-api-web Unified Entry Point
 * @description UI Capability Contract Definitions - Web Platform (Framework-agnostic)
 * @module @brix/runtime-sdk-api-web
 * @version 3.2.1
 *
 * [Module Responsibilities]
 * Defines UI runtime capability contracts for plugins to obtain and use via RuntimeContext.
 *
 * [Capability Categories]
 * - Navigation Capability: Page navigation, router management
 * - Auth Capability: User identity, permission verification
 * - State Capability: Plugin state management
 * - EventBus Capability: Cross-plugin communication
 * - Config Capability: Runtime configuration reading
 * - Http Capability: Unified HTTP requests
 *
 * [Design Principles]
 * - This module is a pure contract definition layer, containing no concrete implementations
 * - Framework-agnostic: No dependency on React/Vue/Angular or other UI frameworks
 * - Plugins only need to depend on this module
 * - For React bindings, use @brix/runtime-sdk-react
 *
 * [v3.2.1 Refactoring Notes (v3.0.4 Architectural Constraint Fix)]
 * - Removed all 963 lines of inline type declarations, eliminating duplicate type export issues
 * - All type definitions are now exported uniformly from types/ directory
 * - Context definitions are exported from context/ directory
 * - Removed React dependency, achieving true framework independence
 *
 * [v3.2 Refactoring Notes]
 * - Split into modular type files (types/)
 * - Removed React dependency, React Hooks migrated to @brix/runtime-sdk-react
 * - RouteContribution.component type changed to framework-agnostic ComponentType
 */

// =========================================
// Re-export all type definitions from types/ directory
// =========================================
export * from './types';

// =========================================
// Re-export runtime context from context/ directory
// =========================================
export * from './context';
