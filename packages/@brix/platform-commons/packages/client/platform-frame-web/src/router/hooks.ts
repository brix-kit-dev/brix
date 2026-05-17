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
 * @file Shell Router Hooks
 * @description Wraps react-router-dom hooks for easier future implementation switching
 * @module @brix-sdk/platform-frame-web/router/hooks
 * @version 3.2.0
 *
 * [Design Notes]
 * Shell layer needs router functionality but does not import react-router-dom directly in each component.
 * Through this file, centralized encapsulation makes it easier to:
 * 1. Unified management of router dependencies
 * 2. Switch to other router implementations in the future
 * 3. Add additional functionality (e.g., logging, monitoring)
 */

import { useNavigate as useRouterNavigate, useLocation as useRouterLocation } from 'react-router-dom';

/**
 * Current path information
 */
export interface CurrentLocation {
  pathname: string;
  search: string;
  hash: string;
}

/**
 * Get current route location
 */
export function useCurrentLocation(): CurrentLocation {
  const location = useRouterLocation();
  return {
    pathname: location.pathname,
    search: location.search,
    hash: location.hash,
  };
}

/**
 * Get current path
 */
export function useCurrentPath(): string {
  const location = useRouterLocation();
  return location.pathname;
}

/**
 * Shell navigation methods
 */
export interface ShellNavigationResult {
  navigateTo: (path: string, replace?: boolean) => void;
  goBack: () => void;
}

/**
 * Shell layer navigation Hook
 */
export function useShellNavigation(): ShellNavigationResult {
  const navigate = useRouterNavigate();

  return {
    navigateTo: (path: string, replace?: boolean) => {
      navigate(path, { replace });
    },
    goBack: () => {
      navigate(-1);
    },
  };
}
