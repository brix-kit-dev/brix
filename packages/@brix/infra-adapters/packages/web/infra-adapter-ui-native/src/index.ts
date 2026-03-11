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
 * @file Native UI Adapter - Package Entry Point
 * @description Entry point for @brix/infra-adapter-ui-native package.
 *              Exports the UIAdapter implementation and all component building blocks.
 * @module @brix/infra-adapter-ui-native
 * @version 3.1.0
 *
 * [Package Overview]
 * This package provides a complete UIAdapter implementation using pure CSS components
 * with zero external UI library dependencies. It implements the UIAdapter contract
 * from @brix/runtime-sdk-api-web.
 *
 * [Primary Export]
 * - nativeUIAdapter: Complete UIAdapter implementation for Host layer registration
 *
 * [Secondary Exports]
 * - Individual components for advanced customization
 * - Theme tokens and provider for theming
 * - Icon components and SVG registry
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This adapter is one of multiple UIAdapter implementations. Host layer selects
 * which adapter to use based on configuration. Shell layer receives the selected
 * adapter's components via useUI() hook.
 *
 * @example
 * ```typescript
 * // Primary usage - Register adapter in Host layer
 * import { nativeUIAdapter } from '@brix/infra-adapter-ui-native';
 *
 * const hostConfig = {
 *   uiAdapter: nativeUIAdapter,
 * };
 *
 * // Advanced usage - Use individual components
 * import { NativeButton, NativeIcon } from '@brix/infra-adapter-ui-native';
 * ```
 */

// ============================================================================
// Primary Export - UIAdapter Implementation
// ============================================================================

export { nativeUIAdapter, default } from './adapter';

// ============================================================================
// Component Exports - For Advanced Customization
// ============================================================================

// Form Components
export { NativeButton } from './components/NativeButton';
export { NativeInput } from './components/NativeInput';
export { NativeSelect } from './components/NativeSelect';

// Display Components
export { NativeCard } from './components/NativeCard';
export { NativeAvatar } from './components/NativeAvatar';
export { NativeBadge } from './components/NativeBadge';
export { NativeTooltip } from './components/NativeTooltip';

// Navigation Components
export { NativeMenu } from './components/NativeMenu';
export { NativeMenuItem } from './components/NativeMenuItem';

// Feedback Components
export { NativeModal } from './components/NativeModal';
export { nativeMessageAPI } from './components/NativeMessage';

// ============================================================================
// Theme Exports
// ============================================================================

export {
  NativeThemeProvider,
  NATIVE_LIGHT_THEME_TOKENS,
  NATIVE_DARK_THEME_TOKENS,
  getNativeThemeTokens,
} from './theme';

// ============================================================================
// Icon Exports
// ============================================================================

export {
  NativeIcon,
  type SvgIconDef,
  SVG_ICON_REGISTRY,
  getIconDef,
  hasIconDef,
  getAvailableIconNames,
} from './icons';
