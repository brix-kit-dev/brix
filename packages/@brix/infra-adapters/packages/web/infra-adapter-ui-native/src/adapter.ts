/**
 * @file Native UI Adapter
 * @description Complete UIAdapter implementation using pure CSS components.
 *              No external UI library dependencies (MUI, Ant Design, etc.).
 * @module @brix/infra-adapter-ui-native/adapter
 * @version 3.1.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - All components use pure CSS/inline styles
 * - Full UIAdapter contract compliance
 * - Theme support via CSS custom properties
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This adapter is selected at Host layer configuration and provides atomic components
 * for Shell layer layout assembly. It implements the UIAdapter interface defined in
 * @brix/runtime-sdk-api-web.
 *
 * [Component Summary]
 * - Form Components: NativeButton, NativeInput, NativeSelect
 * - Display Components: NativeCard, NativeAvatar, NativeBadge, NativeTooltip
 * - Navigation Components: NativeMenu, NativeMenuItem
 * - Feedback Components: NativeModal, nativeMessageAPI
 * - Theme System: NativeThemeProvider, getNativeThemeTokens
 * - Icon System: NativeIcon
 */

import type { UIAdapter } from '@brix/runtime-sdk-api-web';

// Component imports
import { NativeButton } from './components/NativeButton';
import { NativeInput } from './components/NativeInput';
import { NativeSelect } from './components/NativeSelect';
import { NativeCard } from './components/NativeCard';
import { NativeAvatar } from './components/NativeAvatar';
import { NativeBadge } from './components/NativeBadge';
import { NativeTooltip } from './components/NativeTooltip';
import { NativeMenu } from './components/NativeMenu';
import { NativeMenuItem } from './components/NativeMenuItem';
import { NativeModal } from './components/NativeModal';
import { nativeMessageAPI } from './components/NativeMessage';
import { NativeThemeProvider, getNativeThemeTokens } from './theme/NativeThemeProvider';
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
 * import { nativeUIAdapter } from '@brix/infra-adapter-ui-native';
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

  // ========================================
  // Display Components
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

  // ========================================
  // Navigation Components (Atomic Level)
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
};

// ============================================================================
// Default Export
// ============================================================================

export default nativeUIAdapter;
