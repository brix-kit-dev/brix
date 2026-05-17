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
 * @file Design Token Resolver — Strategy Pattern Dependency Injection Point
 * @description Defines the DesignTokenResolver interface that infra-adapters implement
 *              to map UI library theme objects into Brix semantic DesignTokens.
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/design-token-resolver
 * @version 3.2.1
 *
 * [Architectural Position]
 * - Defined in Layer 2A (Capability Contract Layer), alongside DesignTokens
 * - infra-adapter-ui-mui provides MuiDesignTokenResolver (MUI Theme → DesignTokens)
 * - infra-adapter-ui-native provides NativeDesignTokenResolver (ThemeTokens → DesignTokens)
 * - platform-frame-web's ThemeCapabilityImpl receives a resolver via constructor injection
 *
 * [Design Pattern — Strategy]
 * ThemeCapabilityImpl does not know which UI library is in use.
 * It delegates token resolution to the injected DesignTokenResolver implementation.
 * This mirrors the backend pattern: ObservabilityCapability ← OTelObservabilityCapability.
 *
 * [Dependency Flow]
 * ```text
 *   runtime-sdk-api-web          ← defines DesignTokens + DesignTokenResolver
 *         ▲                ▲
 *         │                │
 *   platform-frame-web     infra-adapter-ui-mui
 *   (ThemeCapabilityImpl)  (MuiDesignTokenResolver)
 *         │                        │
 *         │   inject resolver      │
 *         ◄────────────────────────┘
 * ```
 *
 * [Implementation Requirements]
 * Resolver implementations SHOULD:
 * - Cache resolved tokens per mode to avoid redundant mapping on every call
 * - Return Object.freeze()-ed DesignTokens for immutability guarantees
 * - Map ALL fields defined in DesignTokens (no partial results)
 *
 * @see {@link DesignTokens} for the complete token structure
 * @see {@link ThemeCapability.getDesignTokens} for the consumption contract
 * @since 3.2.1
 */

import type { DesignTokens } from './design-tokens';

// ============================================================================
// Design Token Resolver Contract
// ============================================================================

/**
 * Design Token Resolver — maps a specific UI library's theme into Brix semantic tokens.
 *
 * This is the Strategy Pattern injection point that enables platform-frame-web
 * (ThemeCapabilityImpl) to remain UI-library-agnostic. Each infra-adapter provides
 * a concrete resolver implementation:
 *
 * - **MuiDesignTokenResolver** (infra-adapter-ui-mui):
 *   Reads MUI Theme internals (palette.primary.main, typography.h1, etc.)
 *   and maps them into Brix semantic DesignTokens.
 *
 * - **NativeDesignTokenResolver** (infra-adapter-ui-native):
 *   Directly maps ThemeTokens into DesignTokens without an MUI intermediate layer.
 *
 * The resolver is injected into ThemeCapabilityImpl at Host assembly time:
 * ```typescript
 * // host-shell-standalone-web/src/bootstrap.ts
 * const themeCapability = new ThemeCapabilityImpl({
 *   designTokenResolver: new MuiDesignTokenResolver(),
 * });
 * ```
 *
 * @since 3.2.1
 */
export interface DesignTokenResolver {
  /**
   * Resolve the complete design tokens for the given theme mode.
   *
   * Returns a frozen, fully-populated DesignTokens object with all values
   * resolved for the specified mode. Implementations SHOULD cache results
   * per mode to avoid repeated mapping overhead.
   *
   * @param mode - The resolved theme mode ('light' or 'dark').
   *               Note: 'system' is already resolved to 'light' or 'dark'
   *               by ThemeCapabilityImpl before calling this method.
   * @returns A complete, frozen DesignTokens object for the specified mode.
   *
   * @example
   * ```typescript
   * class MuiDesignTokenResolver implements DesignTokenResolver {
   *   private cache = new Map<'light' | 'dark', DesignTokens>();
   *
   *   resolve(mode: 'light' | 'dark'): DesignTokens {
   *     const cached = this.cache.get(mode);
   *     if (cached) return cached;
   *
   *     const tokens = this.mapMuiTheme(mode);
   *     this.cache.set(mode, Object.freeze(tokens));
   *     return tokens;
   *   }
   * }
   * ```
   */
  resolve(mode: 'light' | 'dark'): DesignTokens;
}
