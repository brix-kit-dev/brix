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
 * @file UI Adapter Type Definitions Barrel Export
 * @description Unified export for all UI adapter type definitions
 * @module @brix/runtime-sdk-api-web/types/ui
 * @version 3.2.0
 *
 * [v3.2.0 Refactoring]
 * Split the original 2100+ line ui.ts into component-specific modules:
 * - common.ts: Shared types (ComponentSize)
 * - button.ts: Button component types
 * - input.ts: Input component types
 * - select.ts: Select component types
 * - card.ts: Card component types
 * - avatar.ts: Avatar component types
 * - badge.ts: Badge component types
 * - tooltip.ts: Tooltip component types
 * - menu.ts: Menu and MenuItem component types
 * - modal.ts: Modal component types
 * - message.ts: Message API types
 * - theme-tokens.ts: Theme tokens and preset values
 * - icon.ts: Icon component types
 * - adapter.ts: UIAdapter interface and capability symbol
 *
 * [Design Principles]
 * - Each file has a single responsibility
 * - Facilitates on-demand imports for tree-shaking
 * - Easy to maintain and extend
 * - Full backward compatibility via this barrel export
 */

// =========================================
// Common Types
// =========================================
export * from './common';

// =========================================
// Component Types
// =========================================
export * from './button';
export * from './input';
export * from './select';
export * from './card';
export * from './avatar';
export * from './badge';
export * from './tooltip';
export * from './menu';
export * from './modal';
export * from './message';

// =========================================
// Theme System
// =========================================
export * from './theme-tokens';

// =========================================
// Icon System
// =========================================
export * from './icon';

// =========================================
// UI Adapter Interface
// =========================================
export * from './adapter';
