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
 * @file Navigation-Related Type Definitions
 * @description Defines core types for the navigation system, including navigation options, route change listening, etc.
 * @module @brix/runtime-sdk-api-web/types/navigation
 * @version 3.2.0
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file, and promoted common contracts from infra-adapter-router-web.
 *
 * [Design Principles]
 * - Define common navigation contracts, router adapters implement specific logic
 * - Support declarative navigation (PageId) and imperative navigation (Path)
 */
/**
 * Navigation Options
 *
 * <p>Configuration options controlling navigation behavior.</p>
 */
export interface NavigateOptions {
    /**
     * Whether to Replace Current History Entry
     *
     * <p>When true, the new page replaces the current page's position in the history stack.</p>
     */
    replace?: boolean;
    /**
     * Route State
     *
     * <p>State data passed to the target page.</p>
     */
    state?: Record<string, unknown>;
}
/**
 * Navigation Capability Type Identifier
 */
export declare const NavigationCapabilityType: unique symbol;
/**
 * Navigation Capability Contract
 *
 * <p>Provides page navigation capability for plugins, replacing direct use of react-router.</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const nav = context.getCapability<NavigationCapability>(NavigationCapabilityType);
 * nav.navigate('/booking/list');
 * nav.goBack();
 * ```
 */
export interface NavigationCapability {
    /**
     * Navigate to Specified Path
     *
     * @param path Target path
     * @param options Navigation options
     */
    navigate(path: string, options?: NavigateOptions): void;
    /**
     * Go Back to Previous Page
     */
    goBack(): void;
    /**
     * Get Current Path
     *
     * @returns Current URL path
     */
    getCurrentPath(): string;
}
/**
 * Navigation Options (Compatibility Alias)
 */
export type NavigationOptions = NavigateOptions;
/**
 * Route Change Listener
 *
 * <p>Used to listen for route change events.</p>
 */
export type RouteChangeListener = (path: string) => void;
/**
 * Router Capability Type Identifier (Compatibility Alias)
 */
export declare const RouterCapabilityType: symbol;
/**
 * Router Capability (Compatibility Alias)
 */
export type RouterCapability = NavigationCapability;
//# sourceMappingURL=navigation.d.ts.map