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
 * @file Page Factories Barrel Export
 * @description Re-exports all page factory functions and types
 * @module @brix-sdk/platform-frame-web/pages/factories
 * @version 3.2.0
 *
 * @since 3.2.0 Introduced as part of R6.4 Host slimming migration
 */

export {
  // Factory functions
  createLoginPageFactory,
  createRegisterPageFactory,
  createDashboardPageFactory,
  createErrorPagesFactory,
  createAllPages,
  // Types
  type PageNavigationService,
  type PageAuthService,
  type PageSocialProvider,
  type PageFactoryDeps,
  type AssembledPages,
} from './pageAssembly';
