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
 * @file Components Module Exports
 * @description Exports all UI components from the MUI adapter.
 * @module @brix-sdk/infra-adapter-ui-mui/components
 * @version 3.2.0
 *
 * [Architectural Note - v3.0.8 Blueprint]
 * These are ATOMIC components only. Layout components (Sidebar, Header)
 * are assembled at Shell layer using these atomic building blocks.
 */

// ============================================================================
// Original Components
// ============================================================================

// Form Components
export { MuiButton, default as MuiButtonDefault } from './MuiButton';
export { MuiInput, default as MuiInputDefault } from './MuiInput';
export { MuiSelect, default as MuiSelectDefault } from './MuiSelect';

// Display Components
export { MuiCard, default as MuiCardDefault } from './MuiCard';
export { MuiAvatar, default as MuiAvatarDefault } from './MuiAvatar';
export { MuiBadge, default as MuiBadgeDefault } from './MuiBadge';
export { MuiTooltip, default as MuiTooltipDefault } from './MuiTooltip';

// Navigation Components (Atomic Level)
export { MuiMenu, default as MuiMenuDefault } from './MuiMenu';
export { MuiMenuItem, default as MuiMenuItemDefault } from './MuiMenuItem';

// Feedback Components
export { MuiModal, default as MuiModalDefault } from './MuiModal';
export { muiMessageAPI, default as muiMessageAPIDefault } from './MuiMessage';

// ============================================================================
// Phase 2: BrixUI Extension Components
// ============================================================================

// Layout Components
export { MuiBox, default as MuiBoxDefault } from './MuiBox';
export { MuiStack, default as MuiStackDefault } from './MuiStack';
export { MuiPaper, default as MuiPaperDefault } from './MuiPaper';
export { MuiDivider, default as MuiDividerDefault } from './MuiDivider';

// Typography Components
export { MuiTypography, default as MuiTypographyDefault } from './MuiTypography';

// Data Display Components
export { MuiTable, default as MuiTableDefault } from './MuiTable';
export { MuiTag, default as MuiTagDefault } from './MuiTag';
export { MuiList, MuiListItem, default as MuiListDefault } from './MuiList';
export { MuiEmpty, default as MuiEmptyDefault } from './MuiEmpty';
export { MuiPagination, default as MuiPaginationDefault } from './MuiPagination';

// Extended Form Components
export { MuiCheckbox, default as MuiCheckboxDefault } from './MuiCheckbox';
export { MuiSwitch, default as MuiSwitchDefault } from './MuiSwitch';
export { MuiRadio, MuiRadioGroup, default as MuiRadioDefault } from './MuiRadio';
export { MuiForm, MuiFormItem, default as MuiFormDefault } from './MuiForm';

// Extended Feedback Components
export { MuiAlert, default as MuiAlertDefault } from './MuiAlert';
export { MuiSpin, default as MuiSpinDefault } from './MuiSpin';
export { MuiProgress, default as MuiProgressDefault } from './MuiProgress';

// Extended Navigation Components
export { MuiTabs, MuiTabPane, default as MuiTabsDefault } from './MuiTabs';
export { MuiBreadcrumb, default as MuiBreadcrumbDefault } from './MuiBreadcrumb';

// Container Components
export { MuiDrawer, default as MuiDrawerDefault } from './MuiDrawer';
export { MuiCollapse, MuiCollapsePanel, default as MuiCollapseDefault } from './MuiCollapse';
export { MuiPopover, default as MuiPopoverDefault } from './MuiPopover';

// ============================================================================
// Stability Reform v1.0 — C-7 (Skeleton tri-state placeholder)
// ============================================================================
export { MuiSkeleton } from './MuiSkeleton';
