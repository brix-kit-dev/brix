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
