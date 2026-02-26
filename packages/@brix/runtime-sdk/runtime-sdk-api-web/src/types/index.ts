/**
 * @file Type Definitions Unified Export
 * @description Re-export all type definitions from categorized files
 * @module @brix/runtime-sdk-api-web/types
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
// Plugin Loader Capability Types (v3.2.0 D6 Fix)
// =========================================
export * from './plugin-loader-capability';
