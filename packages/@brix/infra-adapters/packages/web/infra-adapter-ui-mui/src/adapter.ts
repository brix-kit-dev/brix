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
 * @file MUI UI Adapter
 * @description Complete UIAdapter implementation using Material UI v5.
 *              Production-grade atomic components for enterprise applications.
 * @module @brix/infra-adapter-ui-mui/adapter
 * @version 3.1.0
 *
 * [Design Principles]
 * - Full UIAdapter contract compliance
 * - All components built on MUI for enterprise reliability
 * - Theme token system integrated with MUI theming
 * - Comprehensive icon system via @mui/icons-material
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This adapter is selected at Host layer configuration and provides atomic components
 * for Shell layer layout assembly. It implements the UIAdapter interface defined in
 * @brix/runtime-sdk-api-web.
 *
 * [Component Summary]
 * - Form Components: MuiButton, MuiInput, MuiSelect
 * - Display Components: MuiCard, MuiAvatar, MuiBadge, MuiTooltip
 * - Navigation Components: MuiMenu, MuiMenuItem
 * - Feedback Components: MuiModal, muiMessageAPI
 * - Theme System: MuiThemeProvider, getMuiThemeTokens
 * - Icon System: MuiIcon
 *
 * [FORBIDDEN - Layout Components]
 * Layout components (Sidebar, Header, Layout) are NOT included here.
 * They are assembled at Shell layer using these atomic components.
 */

import type { UIAdapter, ThemeTokens } from '@brix/runtime-sdk-api-web';
import { MUI_THEME_TOKENS } from '@brix/runtime-sdk-api-web';

// Component imports
import { MuiButton } from './components/MuiButton';
import { MuiInput } from './components/MuiInput';
import { MuiSelect } from './components/MuiSelect';
import { MuiCard } from './components/MuiCard';
import { MuiAvatar } from './components/MuiAvatar';
import { MuiBadge } from './components/MuiBadge';
import { MuiTooltip } from './components/MuiTooltip';
import { MuiMenu } from './components/MuiMenu';
import { MuiMenuItem } from './components/MuiMenuItem';
import { MuiModal } from './components/MuiModal';
import { muiMessageAPI } from './components/MuiMessage';
import { MuiThemeProvider, getMuiThemeTokens } from './theme/MuiThemeProvider';
import { MuiIcon } from './icons/MuiIcon';

// ============================================================================
// Types
// ============================================================================

/**
 * UI Adapter Configuration Options
 *
 * Configuration for customizing the MUI adapter appearance.
 */
export interface UIAdapterConfig {
  /** Primary brand color */
  primaryColor?: string;
  /** Border radius in pixels */
  borderRadius?: number;
  /** Default theme mode */
  defaultTheme?: 'light' | 'dark';
}

// ============================================================================
// UIAdapter Implementation
// ============================================================================

/**
 * MUI UI Adapter
 *
 * <p>Complete UIAdapter implementation using Material UI v5.
 * This adapter provides production-grade UI components for enterprise applications.</p>
 *
 * <p><strong>Usage:</strong></p>
 * <ul>
 *   <li>Register this adapter in Host layer configuration</li>
 *   <li>Shell layer receives components via useUI() hook</li>
 *   <li>All components follow Material Design guidelines</li>
 * </ul>
 *
 * <p><strong>Architectural Constraints:</strong></p>
 * <ul>
 *   <li>This adapter provides ONLY atomic components</li>
 *   <li>Layout components (Sidebar, Header) are forbidden here</li>
 *   <li>Shell layer assembles layouts using these components</li>
 * </ul>
 *
 * @example
 * ```typescript
 * // In Host layer configuration
 * import { muiUIAdapter } from '@brix/infra-adapter-ui-mui';
 *
 * // Register as UI capability
 * context.registerCapability(UICapabilityType, muiUIAdapter);
 *
 * // In Shell layer component
 * const { Button, Menu, Icon, ThemeProvider } = useUI();
 *
 * return (
 *   <ThemeProvider theme="light">
 *     <div>
 *       <Menu items={menuItems} selectedKey={current} onSelect={handleSelect} />
 *       <Button variant="primary" onClick={handleAction}>
 *         <Icon name="save" /> Save
 *       </Button>
 *     </div>
 *   </ThemeProvider>
 * );
 * ```
 */
export const muiUIAdapter: UIAdapter = {
  // ========================================
  // Form Components
  // ========================================

  /**
   * Button Component
   *
   * <p>MUI-based button with loading state, icons, and variants.</p>
   */
  Button: MuiButton,

  /**
   * Input Component
   *
   * <p>MUI TextField with label, validation, and adornments.</p>
   */
  Input: MuiInput,

  /**
   * Select Component
   *
   * <p>MUI Select with single/multiple mode and search support.</p>
   */
  Select: MuiSelect,

  // ========================================
  // Display Components
  // ========================================

  /**
   * Card Component
   *
   * <p>MUI Card with header, footer, and elevation.</p>
   */
  Card: MuiCard,

  /**
   * Avatar Component
   *
   * <p>MUI Avatar with image, fallback, and icon support.</p>
   */
  Avatar: MuiAvatar,

  /**
   * Badge Component
   *
   * <p>MUI Badge for notifications and status indicators.</p>
   */
  Badge: MuiBadge,

  /**
   * Tooltip Component
   *
   * <p>MUI Tooltip for hover information with positioning.</p>
   */
  Tooltip: MuiTooltip,

  // ========================================
  // Navigation Components (Atomic Level)
  // ========================================

  /**
   * Menu Component
   *
   * <p>MUI List-based navigation menu for Shell layer assembly.</p>
   */
  Menu: MuiMenu,

  /**
   * MenuItem Component
   *
   * <p>MUI ListItem for custom menu rendering scenarios.</p>
   */
  MenuItem: MuiMenuItem,

  // ========================================
  // Feedback Components
  // ========================================

  /**
   * Modal Component
   *
   * <p>MUI Dialog for overlays, confirmations, and forms.</p>
   */
  Modal: MuiModal,

  /**
   * Message API
   *
   * <p>Imperative toast/snackbar notification system.</p>
   */
  message: muiMessageAPI,

  // ========================================
  // Theme System
  // ========================================

  /**
   * Theme Provider Component
   *
   * <p>MUI ThemeProvider with CssBaseline.</p>
   */
  ThemeProvider: MuiThemeProvider,

  /**
   * Get Theme Tokens
   *
   * <p>Returns current theme tokens for style calculations.</p>
   */
  getThemeTokens: getMuiThemeTokens,

  // ========================================
  // Icon System
  // ========================================

  /**
   * Icon Component
   *
   * <p>Name-based icon lookup from @mui/icons-material.</p>
   */
  Icon: MuiIcon,
};

// ============================================================================
// UIAdapter Factory
// ============================================================================

/**
 * Create MUI UI Adapter with Custom Configuration
 *
 * <p>Factory function for creating a customized MUI adapter instance.
 * Allows overriding default theme tokens and behavior.</p>
 *
 * <p><strong>Configuration Options:</strong></p>
 * <ul>
 *   <li>defaultTheme - Initial theme mode (light/dark)</li>
 *   <li>primaryColor - Custom primary brand color</li>
 *   <li>borderRadius - Custom border radius for all components</li>
 *   <li>fontFamily - Custom font family</li>
 * </ul>
 *
 * @param config - UI adapter configuration options
 * @returns Configured UIAdapter instance
 *
 * @example
 * ```typescript
 * // Create custom adapter with purple theme
 * const customAdapter = createMuiUIAdapter({
 *   primaryColor: '#7c3aed',
 *   borderRadius: 12,
 *   defaultTheme: 'light',
 * });
 *
 * // Register in Host layer
 * context.registerCapability(UICapabilityType, customAdapter);
 * ```
 */
export function createMuiUIAdapter(config?: UIAdapterConfig): UIAdapter {
  // Build custom tokens based on config
  const customTokens: ThemeTokens = {
    ...MUI_THEME_TOKENS,
    ...(config?.primaryColor && { primary: config.primaryColor }),
    ...(config?.borderRadius !== undefined && {
      borderRadiusSmall: Math.floor(config.borderRadius * 0.5),
      borderRadiusMedium: config.borderRadius,
      borderRadiusLarge: Math.floor(config.borderRadius * 1.5),
    }),
  };

  // Return adapter with customized getThemeTokens
  return {
    ...muiUIAdapter,
    getThemeTokens: () => customTokens,
  };
}

export default muiUIAdapter;
