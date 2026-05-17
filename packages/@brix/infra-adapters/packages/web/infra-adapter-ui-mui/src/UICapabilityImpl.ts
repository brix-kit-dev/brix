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
 * @file UICapabilityImpl — Formal UIAdapter Capability Wrapper
 * @description Wraps a UIAdapter (MUI, Native, or custom) into a formal
 *              Capability class following the standard CapabilityImpl pattern.
 * @module @brix-sdk/infra-adapter-ui-mui/UICapabilityImpl
 * @version 3.2.1
 *
 * [Architecture Positioning]
 * Infra Adapter layer — bridges concrete UI framework (MUI) to the
 * UIAdapter contract defined in runtime-sdk-api-web.
 *
 * [Architecture Compliance]
 * - Blueprint v3.0.9: All four Capability chains must have formal Impl classes
 * - Phase 2.3: Formal UICapabilityImpl wrapping muiUIAdapter/nativeUIAdapter
 *
 * @since 3.2.1
 * @see UIAdapter — Contract in runtime-sdk-api-web
 * @see muiUIAdapter — MUI implementation in this package
 */

import type { FC, ComponentType } from 'react';
import type { UIAdapter, ThemeTokens } from '@brix-sdk/runtime-sdk-api-web';
import type {
  ButtonProps,
  InputProps,
  SelectProps,
  CardProps,
  AvatarProps,
  BadgeProps,
  TooltipProps,
  MenuProps,
  MenuItemProps,
  ModalProps,
  MessageAPI,
  ThemeProviderProps,
  IconProps,
  BoxProps,
  StackProps,
  PaperProps,
  DividerProps,
  TypographyProps,
  TableProps,
  TagProps,
  ListProps,
  ListItemProps,
  EmptyProps,
  PaginationProps,
  CheckboxProps,
  SwitchProps,
  RadioProps,
  RadioGroupProps,
  FormProps,
  FormItemProps,
  FormComponentType,
  SkeletonProps,
  AlertProps,
  SpinProps,
  ProgressProps,
  TabsProps,
  TabPaneProps,
  BreadcrumbProps,
  StepsProps,
  DrawerProps,
  CollapseProps,
  CollapsePanelProps,
  PopoverProps,
  PopconfirmProps,
  ErrorBoundaryProps,
} from '@brix-sdk/runtime-sdk-api-web';

/**
 * Configuration for UICapabilityImpl.
 */
export interface UICapabilityConfig {
  /** The concrete UIAdapter implementation to wrap */
  adapter: UIAdapter;
}

/**
 * Formal UIAdapter Capability Implementation.
 *
 * Wraps a concrete UIAdapter (MUI, Native, or custom) into a class
 * that follows the standard CapabilityImpl pattern used across the
 * Brix platform (AuthCapabilityImpl, ThemeCapabilityImpl, etc.).
 *
 * @example
 * ```typescript
 * import { muiUIAdapter } from '@brix-sdk/infra-adapter-ui-mui';
 * import { UICapabilityImpl } from '@brix-sdk/infra-adapter-ui-mui';
 *
 * const uiCapability = new UICapabilityImpl({ adapter: muiUIAdapter });
 * runtime.registerCapability(UICapabilityType, { provide: () => uiCapability });
 * ```
 */
export class UICapabilityImpl implements UIAdapter {
  private readonly adapter: UIAdapter;

  constructor(config: UICapabilityConfig) {
    this.adapter = config.adapter;
  }

  // ========================================
  // Form Components
  // ========================================
  get Button(): FC<ButtonProps> { return this.adapter.Button; }
  get Input(): FC<InputProps> { return this.adapter.Input; }
  get Select(): FC<SelectProps> { return this.adapter.Select; }

  // ========================================
  // Display Components
  // ========================================
  get Card(): FC<CardProps> { return this.adapter.Card; }
  get Avatar(): FC<AvatarProps> { return this.adapter.Avatar; }
  get Badge(): FC<BadgeProps> { return this.adapter.Badge; }
  get Tooltip(): FC<TooltipProps> { return this.adapter.Tooltip; }

  // ========================================
  // Navigation Components (Atomic Level)
  // ========================================
  get Menu(): FC<MenuProps> { return this.adapter.Menu; }
  get MenuItem(): FC<MenuItemProps> { return this.adapter.MenuItem; }

  // ========================================
  // Feedback Components
  // ========================================
  get Modal(): FC<ModalProps> { return this.adapter.Modal; }
  get message(): MessageAPI { return this.adapter.message; }

  // ========================================
  // Theme System
  // ========================================
  get ThemeProvider(): FC<ThemeProviderProps> { return this.adapter.ThemeProvider; }
  getThemeTokens(): ThemeTokens { return this.adapter.getThemeTokens(); }

  // ========================================
  // Icon System
  // ========================================
  get Icon(): FC<IconProps> { return this.adapter.Icon; }

  // ========================================
  // v3.2.0 Extended Components
  // ========================================

  // Layout Components
  get Box(): FC<BoxProps> { return this.adapter.Box; }
  get Stack(): FC<StackProps> { return this.adapter.Stack; }
  get Paper(): FC<PaperProps> { return this.adapter.Paper; }
  get Divider(): FC<DividerProps> { return this.adapter.Divider; }

  // Typography
  get Typography(): FC<TypographyProps> { return this.adapter.Typography; }

  // Data Display
  get Table(): FC<TableProps> { return this.adapter.Table; }
  get Tag(): FC<TagProps> { return this.adapter.Tag; }
  get List(): FC<ListProps> { return this.adapter.List; }
  get ListItem(): FC<ListItemProps> { return this.adapter.ListItem; }
  get Empty(): FC<EmptyProps> { return this.adapter.Empty; }
  get Pagination(): FC<PaginationProps> { return this.adapter.Pagination; }

  // Extended Form
  get Checkbox(): FC<CheckboxProps> { return this.adapter.Checkbox; }
  get Switch(): FC<SwitchProps> { return this.adapter.Switch; }
  get Radio(): FC<RadioProps> { return this.adapter.Radio; }
  get RadioGroup(): FC<RadioGroupProps> { return this.adapter.RadioGroup; }
  get Form(): FormComponentType<FormProps> { return this.adapter.Form; }
  get FormItem(): FC<FormItemProps> { return this.adapter.FormItem; }

  // Extended Feedback
  get Alert(): FC<AlertProps> { return this.adapter.Alert; }
  get Spin(): FC<SpinProps> { return this.adapter.Spin; }
  get Progress(): FC<ProgressProps> { return this.adapter.Progress; }

  // Extended Navigation
  get Tabs(): FC<TabsProps> { return this.adapter.Tabs; }
  get TabPane(): FC<TabPaneProps> { return this.adapter.TabPane; }
  get Breadcrumb(): FC<BreadcrumbProps> { return this.adapter.Breadcrumb; }
  get Steps(): FC<StepsProps> { return this.adapter.Steps; }

  // Container Components
  get Drawer(): FC<DrawerProps> { return this.adapter.Drawer; }
  get Collapse(): FC<CollapseProps> { return this.adapter.Collapse; }
  get CollapsePanel(): FC<CollapsePanelProps> { return this.adapter.CollapsePanel; }
  get Popover(): FC<PopoverProps> { return this.adapter.Popover; }
  get Popconfirm(): FC<PopconfirmProps> { return this.adapter.Popconfirm; }

  // ========================================
  // Cross-cutting Components
  // (v3.3.0 Frontend Stability Reform Plan v1.0 — C-1 / C-7)
  // ========================================
  get ErrorBoundary(): ComponentType<ErrorBoundaryProps> { return this.adapter.ErrorBoundary; }
  get Skeleton(): FC<SkeletonProps> { return this.adapter.Skeleton; }
}
