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
 * @file MobileTenantContext — React Context Definition
 * @description Defines the React Context used by MobileTenantProvider
 * and consumed by useMobileTenant() hook.
 *
 * @module @brix-sdk/platform-tenant-mobile/MobileTenantContext
 * @version 3.2.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — React Context for mobile tenant state.
 *
 * @since 3.2.0
 */

import { createContext } from 'react';
import type { MobileTenantContext as MobileTenantContextType } from './types/MobileTenantTypes';

/**
 * React Context for mobile tenant data.
 *
 * Default value is null — components must be wrapped in MobileTenantProvider.
 *
 * @internal
 */
export const MobileTenantReactContext = createContext<MobileTenantContextType | null>(null);
