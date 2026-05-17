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
 * @file Type Definitions Unified Export
 * @description Re-export all type definitions from categorized files
 * @module @brix-sdk/runtime-sdk-api-web/types
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Split the original 1000+ lines in index.ts into the following modules:
 * - capability.ts: Capability system types
 * - plugin.ts: Plugin system types
 * - navigation.ts: Navigation system types
 * - state.ts: State management types
 * - event.ts: Event system types
 * - module.ts: Module system types
 * - http.ts: HTTP client capability types
 * - auth.ts: Authentication capability types
 * - config.ts: Configuration capability types
 * - common.ts: Common utility types and API response types
 *
 * [v3.2.0 Phase 1 Contract Layer Fix]
 * Added the following capability interface type files:
 * - i18n.ts: Internationalization capability types (I18nCapability)
 * - theme.ts: Theme capability types (ThemeCapability)
 * - layout.ts: Layout capability types (LayoutCapability)
 *
 * [Design Principles]
 * - Each file has a single responsibility
 * - Facilitates on-demand imports
 * - Easy to maintain and extend
 */

// =========================================
// Capability System Types
// =========================================
export * from './capability';

// =========================================
// Plugin System Types
// =========================================
export * from './plugin';

// =========================================
// Navigation System Types
// =========================================
export * from './navigation';

// =========================================
// State Management Types
// =========================================
export * from './state';

// =========================================
// Event System Types
// =========================================
export * from './event';

// =========================================
// Module System Types
// =========================================
export * from './module';

// =========================================
// HTTP Client Capability Types
// =========================================
export * from './http';

// =========================================
// Authentication Capability Types
// =========================================
export * from './auth';

// =========================================
// Configuration Capability Types
// =========================================
export * from './config';

// =========================================
// Common Utility Types
// =========================================
export * from './common';

// =========================================
// Internationalization Capability Types (v3.2.0 added)
// =========================================
export * from './i18n';

// =========================================
// Theme Capability Types (v3.2.0 added)
// =========================================
export * from './theme';

// =========================================
// Layout Capability Types (v3.2.0 added)
// =========================================
export * from './layout';

// =========================================
// UI Adapter Capability Types (v3.2.0 Phase 1 UI Adapter)
// =========================================
export * from './ui';

// =========================================
// Tenant Capability Types (v3.1.0 Phase 1.8)
// =========================================
export * from './tenant';

// =========================================
// Tenant Config Capability Types (v3.1.0 Phase 3)
// =========================================
export * from './tenantConfig';

// =========================================
// Plugin Loader Capability Types (v3.2.0 D6 Fix)
// =========================================
export * from './plugin-loader-capability';

// =========================================
// Capability Configuration Types (v3.2.0 Phase 2 Contract Layer Fix)
// =========================================
export * from './capability-config';

// =========================================
// System Event Contracts
// (v3.3.0 Frontend Stability Reform Plan v1.0 — C-2)
// =========================================
export * from './events';

// =========================================
// View Mode Capability Types
// (v3.3.0 Frontend Stability Reform Plan v1.0 — C-4 Phase 2)
// =========================================
export * from './view-mode';

// =========================================
// Module Federation Capability Contracts
// (v3.3.0 Frontend Stability Reform Plan v1.0 — C-1 Phase 1)
// =========================================
export * from './mf';
