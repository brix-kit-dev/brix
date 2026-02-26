/**
 * @file MUI UI Adapter Package Entry Point
 * @description Main export file for @brix/infra-adapter-ui-mui package.
 *              Exports the UIAdapter implementation and all component building blocks.
 * @module @brix/infra-adapter-ui-mui
 * @version 3.1.0
 *
 * This package provides a complete UIAdapter implementation using Material UI v5
 * with enterprise-grade components. It implements the UIAdapter contract defined
 * in @brix/runtime-sdk-api-web.
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This package is part of the infra-adapters layer (Layer 2.5).
 * - Host layer: Selects this adapter via configuration
 * - Shell layer: Uses components via useUI() hook
 * - Plugin layer: Must NOT import directly from this package
 *
 * [Quick Start]
 * ```typescript
 * // Import the adapter
 * import { muiUIAdapter } from '@brix/infra-adapter-ui-mui';
 *
 * // Register in Host layer
 * context.registerCapability(UICapabilityType, muiUIAdapter);
 *
 * // Use in Shell layer
 * const { Button, Menu, Icon } = useUI();
 * ```
 *
 * [Component Categories]
 * - Form Components: Button, Input, Select
 * - Display Components: Card, Avatar, Badge, Tooltip
 * - Navigation Components: Menu, MenuItem (atomic level)
 * - Feedback Components: Modal, message API
 * - Theme System: ThemeProvider, getThemeTokens
 * - Icon System: Icon
 *
 * [FORBIDDEN - Layout Components]
 * This package does NOT export layout components (Sidebar, Header, Layout).
 * These are assembled at Shell layer using the atomic components above.
 */

// ============================================================================
// Main Adapter Export
// ============================================================================

export { muiUIAdapter, createMuiUIAdapter, default } from './adapter';

// ============================================================================
// Component Exports (for advanced use cases)
// ============================================================================

// Form Components
export { MuiButton } from './components/MuiButton';
export { MuiInput } from './components/MuiInput';
export { MuiSelect } from './components/MuiSelect';

// Display Components
export { MuiCard } from './components/MuiCard';
export { MuiAvatar } from './components/MuiAvatar';
export { MuiBadge } from './components/MuiBadge';
export { MuiTooltip } from './components/MuiTooltip';

// Navigation Components (Atomic Level)
export { MuiMenu } from './components/MuiMenu';
export { MuiMenuItem } from './components/MuiMenuItem';

// Feedback Components
export { MuiModal } from './components/MuiModal';
export { muiMessageAPI } from './components/MuiMessage';

// ============================================================================
// Theme Exports
// ============================================================================

export {
  MuiThemeProvider,
  getMuiThemeTokens,
  createMuiTheme,
  useThemeMode,
} from './theme/MuiThemeProvider';

// ============================================================================
// Icon Exports
// ============================================================================

export { MuiIcon } from './icons/MuiIcon';
