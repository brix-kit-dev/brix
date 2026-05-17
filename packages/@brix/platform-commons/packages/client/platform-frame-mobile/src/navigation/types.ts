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
 * Navigation Types
 *
 * @module @brix-sdk/platform-frame-mobile/navigation
 * @since 3.3.0
 */

import type { ComponentType } from 'react';

/**
 * Route Configuration
 */
export interface RouteConfig {
  /** Route name/identifier */
  name: string;
  /** Screen component */
  component: ComponentType<unknown>;
  /** Route options */
  options?: NavigationOptions;
  /** Initial params for the route */
  initialParams?: Record<string, unknown>;
}

/**
 * Navigation Options
 */
export interface NavigationOptions {
  /** Screen title */
  title?: string;
  /** Header visibility */
  headerShown?: boolean;
  /** Tab bar icon name */
  tabBarIcon?: string;
  /** Tab bar label */
  tabBarLabel?: string;
  /** Tab bar badge */
  tabBarBadge?: string | number;
  /** Tab bar visibility */
  tabBarVisible?: boolean;
  /** Gesture enabled (for stack navigation) */
  gestureEnabled?: boolean;
  /** Animation type */
  animation?: 'default' | 'fade' | 'slide_from_right' | 'slide_from_left' | 'none';
}

/**
 * Navigation State
 */
export interface NavigationState {
  /** Current screen name */
  currentScreen: string;
  /** Current screen params */
  params: Record<string, unknown>;
  /** Navigation history stack */
  history: string[];
  /** Registered routes */
  routes: RouteConfig[];
}

/**
 * Deep Link Configuration
 */
export interface DeepLinkConfig {
  /** URL prefix (e.g., 'brix://') */
  prefix: string;
  /** Route mapping */
  routes: Record<string, string>;
}
