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
 * @description Exports all Native UI components implementing UIAdapter contract.
 * @module @brix-sdk/infra-adapter-ui-native/components
 * @version 3.2.0
 */

// ============================================================================
// Form Components
// ============================================================================
export { NativeButton, default as NativeButtonDefault } from './NativeButton';
export { NativeInput, default as NativeInputDefault } from './NativeInput';
export { NativeSelect, default as NativeSelectDefault } from './NativeSelect';
export { NativeCheckbox, default as NativeCheckboxDefault } from './NativeCheckbox';
export { NativeSwitch, default as NativeSwitchDefault } from './NativeSwitch';
export { NativeRadio, NativeRadioGroup, default as NativeRadioDefault } from './NativeRadio';
export { NativeForm, NativeFormItem, default as NativeFormDefault } from './NativeForm';

// ============================================================================
// Layout Components
// ============================================================================
export { NativeBox, default as NativeBoxDefault } from './NativeBox';
export { NativeStack, default as NativeStackDefault } from './NativeStack';
export { NativePaper, default as NativePaperDefault } from './NativePaper';
export { NativeDivider, default as NativeDividerDefault } from './NativeDivider';

// ============================================================================
// Typography
// ============================================================================
export { NativeTypography, default as NativeTypographyDefault } from './NativeTypography';

// ============================================================================
// Data Display Components
// ============================================================================
export { NativeCard, default as NativeCardDefault } from './NativeCard';
export { NativeAvatar, default as NativeAvatarDefault } from './NativeAvatar';
export { NativeBadge, default as NativeBadgeDefault } from './NativeBadge';
export { NativeTooltip, default as NativeTooltipDefault } from './NativeTooltip';
export { NativeTable, default as NativeTableDefault } from './NativeTable';
export { NativeTag, default as NativeTagDefault } from './NativeTag';
export { NativeList, NativeListItem, default as NativeListDefault } from './NativeList';
export { NativeEmpty, default as NativeEmptyDefault } from './NativeEmpty';
export { NativePagination, default as NativePaginationDefault } from './NativePagination';

// ============================================================================
// Navigation Components
// ============================================================================
export { NativeMenu, default as NativeMenuDefault } from './NativeMenu';
export { NativeMenuItem, default as NativeMenuItemDefault } from './NativeMenuItem';
export { NativeTabs, NativeTabPane, default as NativeTabsDefault } from './NativeTabs';
export { NativeBreadcrumb, NativeBreadcrumbItem, default as NativeBreadcrumbDefault } from './NativeBreadcrumb';

// ============================================================================
// Feedback Components
// ============================================================================
export { NativeModal, default as NativeModalDefault } from './NativeModal';
export { nativeMessageAPI } from './NativeMessage';
export { NativeAlert, default as NativeAlertDefault } from './NativeAlert';
export { NativeSpin, default as NativeSpinDefault } from './NativeSpin';
export { NativeProgress, default as NativeProgressDefault } from './NativeProgress';

// ============================================================================
// Stability Reform v1.0 — C-7 (Skeleton tri-state placeholder)
// ============================================================================
export { NativeSkeleton } from './NativeSkeleton';

// ============================================================================
// Container Components
// ============================================================================
export { NativeDrawer, default as NativeDrawerDefault } from './NativeDrawer';
export { NativeCollapse, NativeCollapsePanel, default as NativeCollapseDefault } from './NativeCollapse';
export { NativePopover, default as NativePopoverDefault } from './NativePopover';
