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
 * @file UI Adapter Interface Definition
 * @description Defines the core UIAdapter interface that all UI library implementations must follow
 * @module @brix/runtime-sdk-api-web/types/ui/adapter
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.4 Blueprint]
 * ❌ Layout components (Sidebar, Header, Layout) are FORBIDDEN in UIAdapter
 * ❌ Direct dependency on specific UI libraries (MUI, Ant Design) in contract layer is FORBIDDEN
 * ✅ UIAdapter only defines atomic component contracts
 * ✅ Shell layer assembles layout using atomic components from UIAdapter
 * ✅ Host layer selects UI adapter implementation via configuration
 */

import type { FC } from 'react';
import type { ButtonProps } from './button';
import type { InputProps } from './input';
import type { SelectProps } from './select';
import type { CardProps } from './card';
import type { AvatarProps } from './avatar';
import type { BadgeProps } from './badge';
import type { TooltipProps } from './tooltip';
import type { MenuProps, MenuItemProps } from './menu';
import type { ModalProps } from './modal';
import type { MessageAPI } from './message';
import type { ThemeTokens, ThemeProviderProps } from './theme-tokens';
import type { IconProps } from './icon';

/**
 * UI Adapter Interface
 *
 * Defines the contract for UI library implementations. All UI adapters
 * (MUI, Ant Design, Native CSS) must implement this interface.
 *
 * **Architectural Constraints (v3.0.4 Blueprint):**
 * - This interface only contains ATOMIC components
 * - Layout components (Sidebar, Header, Layout) are FORBIDDEN here
 * - Shell layer assembles layouts using these atomic components
 * - Host layer selects adapter via configuration
 *
 * @example
 * ```typescript
 * // MUI Adapter Implementation
 * export const muiAdapter: UIAdapter = {
 *   Button: MuiButton,
 *   Input: MuiInput,
 *   Select: MuiSelect,
 *   Card: MuiCard,
 *   Avatar: MuiAvatar,
 *   Badge: MuiBadge,
 *   Tooltip: MuiTooltip,
 *   Menu: MuiMenu,
 *   MenuItem: MuiMenuItem,
 *   Modal: MuiModal,
 *   message: createMuiMessageAPI(),
 *   ThemeProvider: MuiThemeProvider,
 *   getThemeTokens: () => MUI_THEME_TOKENS,
 *   Icon: MuiIcon,
 * };
 * ```
 */
export interface UIAdapter {
  // ========================================
  // Form Components
  // ========================================

  /**
   * Button Component
   *
   * Primary action trigger component.
   */
  Button: FC<ButtonProps>;

  /**
   * Input Component
   *
   * Text input field component.
   */
  Input: FC<InputProps>;

  /**
   * Select Component
   *
   * Dropdown selection component.
   */
  Select: FC<SelectProps>;

  // ========================================
  // Display Components
  // ========================================

  /**
   * Card Component
   *
   * Content container component.
   */
  Card: FC<CardProps>;

  /**
   * Avatar Component
   *
   * User avatar display component.
   */
  Avatar: FC<AvatarProps>;

  /**
   * Badge Component
   *
   * Status indicator component.
   */
  Badge: FC<BadgeProps>;

  /**
   * Tooltip Component
   *
   * Hover information component.
   */
  Tooltip: FC<TooltipProps>;

  // ========================================
  // Navigation Components (Atomic Level)
  // NOTE: These are atomic components for Shell layer assembly.
  // Sidebar and Header are assembled in Shell layer using these.
  // ========================================

  /**
   * Menu Component
   *
   * Navigation menu list component. Used by Shell layer to assemble Sidebar.
   */
  Menu: FC<MenuProps>;

  /**
   * Menu Item Component
   *
   * Single menu item for custom rendering scenarios.
   */
  MenuItem: FC<MenuItemProps>;

  // ========================================
  // Feedback Components
  // ========================================

  /**
   * Modal Component
   *
   * Dialog/overlay component.
   */
  Modal: FC<ModalProps>;

  /**
   * Message API
   *
   * Imperative toast/snackbar API.
   */
  message: MessageAPI;

  // ========================================
  // Theme System
  // ========================================

  /**
   * Theme Provider Component
   *
   * Wraps the application to provide theme context.
   */
  ThemeProvider: FC<ThemeProviderProps>;

  /**
   * Get Theme Tokens
   *
   * Returns the current theme tokens for use in styled components.
   */
  getThemeTokens: () => ThemeTokens;

  // ========================================
  // Icon System
  // ========================================

  /**
   * Icon Component
   *
   * Name-based icon lookup component.
   */
  Icon: FC<IconProps>;
}

/**
 * UI Capability Type Symbol
 *
 * Symbol used to retrieve UIAdapter from RuntimeContext.
 * Use with context.getCapability() to obtain the UI adapter instance.
 *
 * @example
 * ```typescript
 * // Get UI adapter from context
 * const ui = context.getCapability<UIAdapter>(UICapabilityType);
 * if (ui) {
 *   const { Button, Menu, Icon } = ui;
 *   // Use components...
 * }
 * ```
 *
 * @see UIAdapter
 * @see RuntimeContext
 */
export const UICapabilityType = Symbol.for('UICapability');
