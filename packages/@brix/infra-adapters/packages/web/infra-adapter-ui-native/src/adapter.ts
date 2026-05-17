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
 * @file Native UI Adapter
 * @description Complete UIAdapter implementation using pure CSS components.
 *              No external UI library dependencies (MUI, Ant Design, etc.).
 * @module @brix-sdk/infra-adapter-ui-native/adapter
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - All components use pure CSS/inline styles
 * - Full UIAdapter contract compliance
 * - Theme support via CSS custom properties
 *
 * [Architectural Position - v3.0.8 Blueprint]
 * This adapter is selected at Host layer configuration and provides atomic components
 * for Shell layer layout assembly. It implements the UIAdapter interface defined in
 * @brix-sdk/runtime-sdk-api-web.
 *
 * [Component Summary - v3.2.0]
 * - Layout Components: NativeBox, NativeStack, NativePaper, NativeDivider
 * - Typography: NativeTypography
 * - Form Components: NativeButton, NativeInput, NativeSelect, NativeCheckbox,
 *                    NativeSwitch, NativeRadio, NativeRadioGroup, NativeForm, NativeFormItem
 * - Data Display: NativeCard, NativeAvatar, NativeBadge, NativeTooltip, NativeTable,
 *                 NativeTag, NativeList, NativeListItem, NativeEmpty, NativePagination
 * - Navigation Components: NativeMenu, NativeMenuItem, NativeTabs, NativeTabPane, NativeBreadcrumb
 * - Feedback Components: NativeModal, nativeMessageAPI, NativeAlert, NativeSpin, NativeProgress
 * - Container Components: NativeDrawer, NativeCollapse, NativeCollapsePanel, NativePopover
 * - Theme System: NativeThemeProvider, getNativeThemeTokens
 * - Icon System: NativeIcon
 */

import type { UIAdapter } from '@brix-sdk/runtime-sdk-api-web';

// Form Component Imports
import { NativeButton } from './components/NativeButton';
import { NativeInput } from './components/NativeInput';
import { NativeSelect } from './components/NativeSelect';
import { NativeCheckbox } from './components/NativeCheckbox';
import { NativeSwitch } from './components/NativeSwitch';
import { NativeRadio, NativeRadioGroup } from './components/NativeRadio';
import { NativeForm, NativeFormItem } from './components/NativeForm';

// Layout Component Imports
import { NativeBox } from './components/NativeBox';
import { NativeStack } from './components/NativeStack';
import { NativePaper } from './components/NativePaper';
import { NativeDivider } from './components/NativeDivider';

// Typography Import
import { NativeTypography } from './components/NativeTypography';

// Data Display Component Imports
import { NativeCard } from './components/NativeCard';
import { NativeAvatar } from './components/NativeAvatar';
import { NativeBadge } from './components/NativeBadge';
import { NativeTooltip } from './components/NativeTooltip';
import { NativeTable } from './components/NativeTable';
import { NativeTag } from './components/NativeTag';
import { NativeList, NativeListItem } from './components/NativeList';
import { NativeEmpty } from './components/NativeEmpty';
import { NativePagination } from './components/NativePagination';

// Navigation Component Imports
import { NativeMenu } from './components/NativeMenu';
import { NativeMenuItem } from './components/NativeMenuItem';
import { NativeTabs, NativeTabPane } from './components/NativeTabs';
import { NativeBreadcrumb } from './components/NativeBreadcrumb';
import { NativeSteps } from './components/NativeSteps';

// Feedback Component Imports
import { NativeModal } from './components/NativeModal';
import { nativeMessageAPI } from './components/NativeMessage';
import { NativeAlert } from './components/NativeAlert';
import { NativeSpin } from './components/NativeSpin';
import { NativeProgress } from './components/NativeProgress';

// Container Component Imports
import { NativeDrawer } from './components/NativeDrawer';
import { NativeCollapse, NativeCollapsePanel } from './components/NativeCollapse';
import { NativePopover } from './components/NativePopover';
import { NativePopconfirm } from './components/NativePopconfirm';
import { NativeErrorBoundary } from './components/NativeErrorBoundary';
import { NativeSkeleton } from './components/NativeSkeleton';

// Theme System Imports
import { NativeThemeProvider, getNativeThemeTokens } from './theme/NativeThemeProvider';

// Icon System Import
import { NativeIcon } from './icons/NativeIcon';

// ============================================================================
// UIAdapter Implementation
// ============================================================================

/**
 * Native UI Adapter
 *
 * <p>Complete UIAdapter implementation using pure CSS components.
 * This adapter has zero external UI library dependencies.</p>
 *
 * <p><strong>Usage:</strong></p>
 * <ul>
 *   <li>Register this adapter in Host layer configuration</li>
 *   <li>Shell layer receives components via useUI() hook</li>
 *   <li>All components follow MUI visual guidelines</li>
 * </ul>
 *
 * @example
 * ```typescript
 * // In Host layer configuration
 * import { nativeUIAdapter } from '@brix-sdk/infra-adapter-ui-native';
 *
 * const hostConfig = {
 *   uiAdapter: nativeUIAdapter,
 * };
 *
 * // In Shell layer component
 * const { Button, Menu, Icon } = useUI();
 *
 * return (
 *   <div>
 *     <Menu items={menuItems} selectedKey={current} onSelect={handleSelect} />
 *     <Button variant="primary" onClick={handleAction}>
 *       <Icon name="save" /> Save
 *     </Button>
 *   </div>
 * );
 * ```
 */
export const nativeUIAdapter: UIAdapter = {
  // ========================================
  // Layout Components
  // ========================================

  /**
   * Box Component
   *
   * <p>Universal layout container with component polymorphism.</p>
   */
  Box: NativeBox,

  /**
   * Stack Component
   *
   * <p>Flexbox layout with gap and direction control.</p>
   */
  Stack: NativeStack,

  /**
   * Paper Component
   *
   * <p>Elevated surface container with shadow system.</p>
   */
  Paper: NativePaper,

  /**
   * Divider Component
   *
   * <p>Visual separator with optional text label.</p>
   */
  Divider: NativeDivider,

  // ========================================
  // Typography
  // ========================================

  /**
   * Typography Component
   *
   * <p>Text styling with Material Design type scale.</p>
   */
  Typography: NativeTypography,

  // ========================================
  // Form Components
  // ========================================

  /**
   * Button Component
   *
   * <p>Primary action trigger using pure CSS styling.</p>
   */
  Button: NativeButton,

  /**
   * Input Component
   *
   * <p>Text input field with label and validation support.</p>
   */
  Input: NativeInput,

  /**
   * Select Component
   *
   * <p>Native HTML select with styled wrapper.</p>
   */
  Select: NativeSelect,

  /**
   * Checkbox Component
   *
   * <p>Binary selection control with label support.</p>
   */
  Checkbox: NativeCheckbox,

  /**
   * Switch Component
   *
   * <p>Toggle control for on/off states.</p>
   */
  Switch: NativeSwitch,

  /**
   * Radio Component
   *
   * <p>Single option selection from a group.</p>
   */
  Radio: NativeRadio,

  /**
   * RadioGroup Component
   *
   * <p>Container for radio button groups.</p>
   */
  RadioGroup: NativeRadioGroup,

  /**
   * Form Component
   *
   * <p>Form container with layout control.</p>
   */
  Form: NativeForm,

  /**
   * FormItem Component
   *
   * <p>Form field wrapper with label and validation.</p>
   */
  FormItem: NativeFormItem,

  // ========================================
  // Data Display Components
  // ========================================

  /**
   * Card Component
   *
   * <p>Content container with header, footer, and elevation.</p>
   */
  Card: NativeCard,

  /**
   * Avatar Component
   *
   * <p>User avatar with fallback and icon support.</p>
   */
  Avatar: NativeAvatar,

  /**
   * Badge Component
   *
   * <p>Small status indicator with count display.</p>
   */
  Badge: NativeBadge,

  /**
   * Tooltip Component
   *
   * <p>Hover information popup with positioning.</p>
   */
  Tooltip: NativeTooltip,

  /**
   * Table Component
   *
   * <p>Data table with sorting, selection, and pagination.</p>
   */
  Table: NativeTable,

  /**
   * Tag Component
   *
   * <p>Label/chip for categorization and status.</p>
   */
  Tag: NativeTag,

  /**
   * List Component
   *
   * <p>Vertical list container for items.</p>
   */
  List: NativeList,

  /**
   * ListItem Component
   *
   * <p>Individual item in a list.</p>
   */
  ListItem: NativeListItem,

  /**
   * Empty Component
   *
   * <p>Empty state placeholder with illustration.</p>
   */
  Empty: NativeEmpty,

  /**
   * Pagination Component
   *
   * <p>Page navigation for data sets.</p>
   */
  Pagination: NativePagination,

  // ========================================
  // Navigation Components
  // ========================================

  /**
   * Menu Component
   *
   * <p>Hierarchical navigation menu for Shell layer assembly.</p>
   */
  Menu: NativeMenu,

  /**
   * MenuItem Component
   *
   * <p>Individual menu item for custom rendering.</p>
   */
  MenuItem: NativeMenuItem,

  /**
   * Tabs Component
   *
   * <p>Tab-based navigation with content panels.</p>
   */
  Tabs: NativeTabs,

  /**
   * TabPane Component
   *
   * <p>Individual tab panel in Tabs.</p>
   */
  TabPane: NativeTabPane,

  /**
   * Breadcrumb Component
   *
   * <p>Hierarchical navigation path indicator.</p>
   */
  Breadcrumb: NativeBreadcrumb,

  /**
   * Steps Component
   *
   * <p>Step-by-step navigation for multi-step workflows.</p>
   */
  Steps: NativeSteps,

  // ========================================
  // Feedback Components
  // ========================================

  /**
   * Modal Component
   *
   * <p>Dialog/overlay with header, footer, and actions.</p>
   */
  Modal: NativeModal,

  /**
   * Message API
   *
   * <p>Imperative toast/snackbar notification system.</p>
   */
  message: nativeMessageAPI,

  /**
   * Alert Component
   *
   * <p>Inline feedback message with severity levels.</p>
   */
  Alert: NativeAlert,

  /**
   * Spin Component
   *
   * <p>Loading indicator with optional content wrapper.</p>
   */
  Spin: NativeSpin,

  /**
   * Progress Component
   *
   * <p>Progress indicator (linear or circular).</p>
   */
  Progress: NativeProgress,

  // ========================================
  // Container Components
  // ========================================

  /**
   * Drawer Component
   *
   * <p>Slide-in panel from any edge of the viewport.</p>
   */
  Drawer: NativeDrawer,

  /**
   * Collapse Component
   *
   * <p>Collapsible content panels (accordion).</p>
   */
  Collapse: NativeCollapse,

  /**
   * CollapsePanel Component
   *
   * <p>Individual panel in Collapse.</p>
   */
  CollapsePanel: NativeCollapsePanel,

  /**
   * Popover Component
   *
   * <p>Floating content panel triggered by interaction.</p>
   */
  Popover: NativePopover,

  /**
   * Popconfirm Component
   *
   * <p>Confirmation dialog triggered by user interaction.</p>
   */
  Popconfirm: NativePopconfirm,

  // ========================================
  // Theme System
  // ========================================

  /**
   * Theme Provider Component
   *
   * <p>Provides theme context via CSS custom properties.</p>
   */
  ThemeProvider: NativeThemeProvider,

  /**
   * Get Theme Tokens
   *
   * <p>Returns current theme tokens for style calculations.</p>
   */
  getThemeTokens: getNativeThemeTokens,

  // ========================================
  // Icon System
  // ========================================

  /**
   * Icon Component
   *
   * <p>Inline SVG icon with name-based lookup.</p>
   */
  Icon: NativeIcon,

  // ========================================
  // Cross-cutting Components
  // (v3.3.0 Frontend Stability Reform Plan v1.0 — C-1)
  // ========================================

  /**
   * ErrorBoundary Component
   *
   * <p>React render-time exception isolator with pluggable fallback.</p>
   */
  ErrorBoundary: NativeErrorBoundary,

  /**
   * Skeleton Component (v3.3.0 Frontend Stability Reform Plan v1.0 — C-7)
   *
   * <p>CLS-stable structural placeholder. Consumed automatically by
   * `usePageState().render()` and may be used directly by plugins for
   * custom skeleton layouts.</p>
   */
  Skeleton: NativeSkeleton,
};

// ============================================================================
// Default Export
// ============================================================================

export default nativeUIAdapter;
