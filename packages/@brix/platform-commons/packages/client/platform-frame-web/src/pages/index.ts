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
 * @file pages Module Exports
 * @description Page exports for Web host package
 * @module @brix-sdk/platform-frame-web/pages
 * @version 3.2.0
 */

// Dashboard
export { createDashboardPage, createSimpleDashboardPage } from './DashboardPage';

// Error Pages
export {
  createNotFoundPage,
  createSimpleNotFoundPage,
  createUnauthorizedPage,
  createSimpleUnauthorizedPage,
} from './ErrorPages';

// Placeholder Page (v3.2.0)
export {
  PlaceholderPage,
  createSimplePlaceholderPage,
  type PlaceholderPageProps,
  type SimplePlaceholderConfig,
} from './PlaceholderPage';

// Page Assembly Factories (v3.2.0 - migrated from Host layer per R6.4)
export {
  createLoginPageFactory,
  createRegisterPageFactory,
  createDashboardPageFactory,
  createErrorPagesFactory,
  createAllPages,
  type PageNavigationService,
  type PageAuthService,
  type PageSocialProvider,
  type PageFactoryDeps,
  type AssembledPages,
} from './factories';

// Types
export type {
  // Common types
  ShellBranding,
  NavigationService,
  ShellRoutes,
  // Dashboard types
  StatCard,
  QuickAction,
  DashboardDataProvider,
  DashboardPageConfig,
  SimpleDashboardConfig,
  // Error types
  ErrorPageConfig,
  SimpleErrorPageConfig,
} from './types';
