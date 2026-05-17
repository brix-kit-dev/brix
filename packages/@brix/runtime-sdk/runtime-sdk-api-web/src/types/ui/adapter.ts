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
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/adapter
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.4 Blueprint]
 * �?Layout components (Sidebar, Header, Layout) are FORBIDDEN in UIAdapter
 * �?Direct dependency on specific UI libraries (MUI, Ant Design) in contract layer is FORBIDDEN
 * �?UIAdapter only defines atomic component contracts
 * �?Shell layer assembles layout using atomic components from UIAdapter
 * �?Host layer selects UI adapter implementation via configuration
 */

import type { ComponentType, FC } from 'react';
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

// =========================================
// v3.2.0 Extended Component Imports
// Phase 1: UIAdapter Contract Extension (BrixUI Governance Plan)
// =========================================
import type { BoxProps } from './box';
import type { StackProps } from './stack';
import type { PaperProps } from './paper';
import type { DividerProps } from './divider';
import type { TypographyProps } from './typography';
import type { TableProps } from './table';
import type { TagProps } from './tag';
import type { ListProps, ListItemProps } from './list';
import type { EmptyProps } from './empty';
import type { PaginationProps } from './pagination';
import type { CheckboxProps } from './checkbox';
import type { SwitchProps } from './switch';
import type { RadioProps, RadioGroupProps } from './radio';
import type { FormItemProps } from './form';
// Note: `FormProps` is no longer imported here directly because the C-8
// reform replaced `Form: FC<FormProps>` with `Form: FormComponentType`
// (compound component). `FormComponentType` internally references
// `FormProps`, so the runtime contract is unchanged.
import type { AlertProps } from './alert';
import type { SpinProps } from './spin';
import type { ProgressProps } from './progress';
import type { TabsProps, TabPaneProps } from './tabs';
import type { BreadcrumbProps } from './breadcrumb';
import type { StepsProps } from './steps';
import type { DrawerProps } from './drawer';
import type { CollapseProps, CollapsePanelProps } from './collapse';
import type { PopoverProps } from './popover';
import type { PopconfirmProps } from './popconfirm';
import type { ErrorBoundaryProps } from './error-boundary';
// Frontend Stability Reform v1.0 — C-7 / C-8 (Phase 4)
import type { SkeletonProps } from './skeleton';
import type { FormComponentType } from './form';

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
 *   getThemeTokens: () => BRIX_LIGHT_THEME_TOKENS, // from '@brix-sdk/platform-design-tokens'
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

  // ========================================
  // v3.2.0 Extended Components
  // Phase 1: UIAdapter Contract Extension (BrixUI Governance Plan)
  // ========================================

  // ----------------------------------------
  // Layout Components
  // ----------------------------------------

  /**
   * Box Component
   *
   * Universal layout container for custom styling.
   * Replaces direct div usage with themeable container.
   */
  Box: FC<BoxProps>;

  /**
   * Stack Component
   *
   * Flexbox layout container for arranging children
   * with consistent spacing and alignment.
   */
  Stack: FC<StackProps>;

  /**
   * Paper Component
   *
   * Elevated surface component for content grouping.
   * Provides shadow elevation and card-like appearance.
   */
  Paper: FC<PaperProps>;

  /**
   * Divider Component
   *
   * Visual separator for content sections.
   * Supports horizontal and vertical orientations.
   */
  Divider: FC<DividerProps>;

  // ----------------------------------------
  // Typography Component
  // ----------------------------------------

  /**
   * Typography Component
   *
   * Semantic text component with consistent styling.
   * Supports heading (h1-h6), body, and utility variants.
   */
  Typography: FC<TypographyProps>;

  // ----------------------------------------
  // Data Display Components
  // ----------------------------------------

  /**
   * Table Component
   *
   * Data table with column definitions, sorting, and selection.
   * Generic component supporting typed data rows.
   */
  Table: FC<TableProps>;

  /**
   * Tag Component
   *
   * Label component for categorization and status display.
   * Wraps MUI Chip / Ant Design Tag.
   */
  Tag: FC<TagProps>;

  /**
   * List Component
   *
   * Container for vertical lists of items.
   * Provides consistent spacing and optional dividers.
   */
  List: FC<ListProps>;

  /**
   * ListItem Component
   *
   * Individual item within a List component.
   * Supports avatar, primary/secondary text, and actions.
   */
  ListItem: FC<ListItemProps>;

  /**
   * Empty Component
   *
   * Placeholder for empty data states.
   * Displays illustration and description with optional actions.
   */
  Empty: FC<EmptyProps>;

  /**
   * Pagination Component
   *
   * Navigation for paged data display.
   * Supports page navigation and size selection.
   */
  Pagination: FC<PaginationProps>;

  // ----------------------------------------
  // Extended Form Components
  // ----------------------------------------

  /**
   * Checkbox Component
   *
   * Binary selection control with optional indeterminate state.
   */
  Checkbox: FC<CheckboxProps>;

  /**
   * Switch Component
   *
   * Toggle control for binary on/off settings.
   * Provides immediate visual feedback.
   */
  Switch: FC<SwitchProps>;

  /**
   * Radio Component
   *
   * Single radio button for use within RadioGroup.
   */
  Radio: FC<RadioProps>;

  /**
   * RadioGroup Component
   *
   * Container for managing mutually exclusive Radio options.
   */
  RadioGroup: FC<RadioGroupProps>;

  /**
   * Form Component
   *
   * Container for form fields with layout control.
   * Supports horizontal, vertical, and inline layouts.
   *
   * <h3>v3.3.0 Phase 4 — C-8 Compound Component</h3>
   * `Form` is now a compound component: in addition to being a normal
   * function component, it exposes a `useForm<T>()` hook that returns a
   * {@link FormInstance}. This realises the plan §6.2 wording
   * `useUI().Form.useForm()` literally and is the **only** sanctioned form
   * state surface for plugins.
   *
   * The implementation of `useForm` is single-sourced in
   * `@brix-sdk/runtime-sdk-react` and attached identically by every adapter,
   * so behaviour is guaranteed identical across MUI / Native / future UI
   * libraries.
   */
  Form: FormComponentType;

  /**
   * FormItem Component
   *
   * Wrapper for form fields with label and validation display.
   */
  FormItem: FC<FormItemProps>;

  // ----------------------------------------
  // Extended Feedback Components
  // ----------------------------------------

  /**
   * Alert Component
   *
   * Static notification banner for contextual feedback.
   * Supports success, info, warning, and error severities.
   */
  Alert: FC<AlertProps>;

  /**
   * Spin Component
   *
   * Loading indicator for async operations.
   * Can be standalone or wrapper for content overlay.
   */
  Spin: FC<SpinProps>;

  /**
   * Progress Component
   *
   * Progress indicator for operation completion status.
   * Supports linear and circular variants.
   */
  Progress: FC<ProgressProps>;

  // ----------------------------------------
  // Navigation Components
  // ----------------------------------------

  /**
   * Tabs Component
   *
   * Tabbed navigation for content switching.
   * Supports line, card, and editable-card styles.
   */
  Tabs: FC<TabsProps>;

  /**
   * TabPane Component
   *
   * Individual tab panel within Tabs.
   */
  TabPane: FC<TabPaneProps>;

  /**
   * Breadcrumb Component
   *
   * Hierarchical navigation trail.
   * Shows current location within site structure.
   */
  Breadcrumb: FC<BreadcrumbProps>;

  /**
   * Steps Component
   *
   * Stepper/wizard navigation for multi-step workflows.
   * Provides visual progress indication for sequential processes.
   */
  Steps: FC<StepsProps>;

  // ----------------------------------------
  // Container Components
  // ----------------------------------------

  /**
   * Drawer Component
   *
   * Slide-in panel for secondary content.
   * Supports left, right, top, bottom placements.
   */
  Drawer: FC<DrawerProps>;

  /**
   * Collapse Component
   *
   * Expandable content sections (Accordion).
   * Supports single or multiple expanded panels.
   */
  Collapse: FC<CollapseProps>;

  /**
   * CollapsePanel Component
   *
   * Individual expandable panel within Collapse.
   */
  CollapsePanel: FC<CollapsePanelProps>;

  /**
   * Popover Component
   *
   * Floating card with rich content on trigger.
   * Extends Tooltip with card-like appearance.
   */
  Popover: FC<PopoverProps>;

  /**
   * Popconfirm Component
   *
   * Inline confirmation popover for destructive actions.
   * Lightweight alternative to Modal for simple confirm/cancel flows.
   */
  Popconfirm: FC<PopconfirmProps>;

  // ----------------------------------------
  // Cross-cutting Components
  // (v3.3.0 Frontend Stability Reform Plan v1.0 — C-1)
  // ----------------------------------------

  /**
   * ErrorBoundary Component
   *
   * Cross-cutting React error boundary that isolates plugin / route render
   * crashes from the host shell. Provided centrally by the UIAdapter so that
   * plugins (R-3) cannot bypass the global error envelope by importing their
   * own boundary implementation from a UI library.
   *
   * Catches synchronous render-time errors only. Asynchronous failures must
   * be reported through `HttpCapability` interceptors or the `EventBus`.
   *
   * Typed as `ComponentType` because React error boundaries require class
   * components — function components cannot implement `getDerivedStateFromError`
   * / `componentDidCatch`. `ComponentType` accepts both class and function
   * components, keeping the contract permissive for adapter implementations.
   */
  ErrorBoundary: ComponentType<ErrorBoundaryProps>;

  /**
   * Skeleton Component
   *
   * <h3>v3.3.0 Phase 4 — C-7 Three-state Unified Display</h3>
   * Structural placeholder used by `usePageState().render()` and any plugin
   * that wants a content-shape preserving loading state. Renders a
   * grey-pulsing block (or wave / none animation) with the given variant
   * (text / title / paragraph / circular / rectangular).
   *
   * Skeleton is preferred over Spin for first-load list / detail / card
   * pages because it eliminates Cumulative Layout Shift — a Core Web Vitals
   * metric the platform tracks.
   */
  Skeleton: FC<SkeletonProps>;
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
