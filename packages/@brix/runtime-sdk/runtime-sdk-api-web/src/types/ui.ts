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
 * @file UI Adapter Type Definitions - Backward Compatibility Re-export
 * @description Re-exports all UI adapter types from the ui/ subfolder for backward compatibility.
 * @module @brix/runtime-sdk-api-web/types/ui
 * @version 3.2.0
 *
 * [v3.2.0 Refactoring]
 * The original 2100+ line file has been split into component-specific modules in the ui/ folder:
 * - ui/common.ts: Shared types (ComponentSize)
 * - ui/button.ts: Button component types
 * - ui/input.ts: Input component types
 * - ui/select.ts: Select component types
 * - ui/card.ts: Card component types
 * - ui/avatar.ts: Avatar component types
 * - ui/badge.ts: Badge component types
 * - ui/tooltip.ts: Tooltip component types
 * - ui/menu.ts: Menu and MenuItem component types
 * - ui/modal.ts: Modal component types
 * - ui/message.ts: Message API types
 * - ui/theme-tokens.ts: Theme tokens and preset values
 * - ui/icon.ts: Icon component types
 * - ui/adapter.ts: UIAdapter interface and capability symbol
 *
 * This file re-exports everything from the ui/ folder for backward compatibility.
 * New code should import directly from '@brix/runtime-sdk-api-web/types/ui/[component]'.
 *
 * [Architectural Constraints - v3.0.4 Blueprint]
 * - UIAdapter only contains ATOMIC components
 * - Layout components (Sidebar, Header, Layout) are FORBIDDEN in UIAdapter
 * - Shell layer assembles layouts using these atomic components
 * - Host layer selects adapter via configuration
 */

export * from './ui/index';
