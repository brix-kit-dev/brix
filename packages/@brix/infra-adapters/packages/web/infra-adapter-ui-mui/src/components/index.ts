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
 * @module @brix/infra-adapter-ui-mui/components
 * @version 3.1.0
 *
 * [Architectural Note - v3.0.4 Blueprint]
 * These are ATOMIC components only. Layout components (Sidebar, Header)
 * are assembled at Shell layer using these atomic building blocks.
 */

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
