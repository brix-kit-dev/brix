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
 * @module @brix-sdk/runtime-sdk-api-web/types/ui
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
// v3.2.0 Extended Component Types
// Phase 1: UIAdapter Contract Extension (BrixUI Governance Plan)
// =========================================

// Layout Components
export * from './box';
export * from './stack';
export * from './paper';
export * from './divider';

// Typography
export * from './typography';

// Data Display Components
export * from './table';
export * from './tag';
export * from './list';
export * from './empty';
export * from './pagination';

// Form Components
export * from './checkbox';
export * from './switch';
export * from './radio';
export * from './form';

// Feedback Components
export * from './alert';
export * from './spin';
export * from './progress';
// Frontend Stability Reform v1.0 — C-7: Skeleton placeholder component
export * from './skeleton';

// Navigation Components
export * from './tabs';
export * from './breadcrumb';
export * from './steps';

// Container Components
export * from './drawer';
export * from './collapse';
export * from './popover';
export * from './popconfirm';

// =========================================
// Theme System
// =========================================
export * from './theme-tokens';

// =========================================
// Design Tokens (v3.2.1 — Brix Semantic Token Contract)
// =========================================
export * from './design-tokens';
export * from './design-token-resolver';

// =========================================
// Icon System
// =========================================
export * from './icon';

// =========================================
// Cross-cutting Components
// (v3.3.0 Frontend Stability Reform Plan v1.0 — C-1)
// =========================================
export * from './error-boundary';

// =========================================
// UI Adapter Interface
// =========================================
export * from './adapter';
