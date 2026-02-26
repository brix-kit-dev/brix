/**
 * @file UI Adapter Capability Type Definitions
 * @description Defines core types for the UI adapter system, including atomic component props,
 *              theme tokens, icon system, and the UIAdapter interface.
 * @module @brix/runtime-sdk-api-web/types/ui
 * @version 3.2.0
 *
 * [v3.2.0 Addition]
 * Phase 1 UI Adapter contract layer: Defines UIAdapter interface for UI library abstraction.
 *
 * [Design Principles]
 * - UIAdapter provides ONLY atomic components (Button, Input, Icon, etc.)
 * - Layout components (Sidebar, Header, Layout) are assembled at Shell layer using atomic components
 * - Theme tokens follow MUI standard color palette for broad compatibility
 * - All types are framework-agnostic, but React bindings are provided for convenience
 *
 * [Architectural Constraints - v3.0.4 Blueprint]
 * ❌ Layout components (Sidebar, Header, Layout) are FORBIDDEN in UIAdapter
 * ❌ Direct dependency on specific UI libraries (MUI, Ant Design) in contract layer is FORBIDDEN
 * ✅ UIAdapter only defines atomic component contracts
 * ✅ Shell layer assembles layout using atomic components from UIAdapter
 * ✅ Host layer selects UI adapter implementation via configuration
 *
 * [Component Categories]
 * - Form Components: Button, Input, Select
 * - Display Components: Card, Avatar, Badge, Tooltip
 * - Navigation Components: Menu, MenuItem (atomic level, for Shell layout assembly)
 * - Feedback Components: Modal, message API
 * - Theme System: ThemeProvider, ThemeTokens
 * - Icon System: Icon component with name-based lookup
 */

import type {
  ReactNode,
  FC,
  MouseEvent,
  ChangeEvent,
  KeyboardEvent,
  FocusEvent,
  CSSProperties,
} from 'react';

// ============================================================================
// Common Types
// ============================================================================

/**
 * Component Size Variants
 *
 * <p>Standard size variants used across all UI components for visual consistency.</p>
 */
export type ComponentSize = 'small' | 'medium' | 'large';

// ============================================================================
// Button Component
// ============================================================================

/**
 * Button Style Variants
 *
 * <p>Defines the visual style of the button.</p>
 * <ul>
 *   <li>primary - Solid background, high emphasis</li>
 *   <li>secondary - Outlined style, medium emphasis</li>
 *   <li>text - Text only, low emphasis</li>
 *   <li>danger - Destructive action indicator</li>
 * </ul>
 */
export type ButtonVariant = 'primary' | 'secondary' | 'text' | 'danger';

/**
 * Button Component Props
 *
 * <p>UI library agnostic button properties. All UI adapter implementations
 * must support these properties to ensure consistent behavior across
 * different UI framework implementations.</p>
 *
 * @example
 * ```tsx
 * <Button
 *   variant="primary"
 *   size="medium"
 *   startIcon="save"
 *   onClick={handleSave}
 * >
 *   Save Changes
 * </Button>
 * ```
 */
export interface ButtonProps {
  /**
   * Button Visual Variant
   *
   * <p>Determines the visual style of the button.</p>
   * @default 'primary'
   */
  variant?: ButtonVariant;

  /**
   * Button Size
   *
   * <p>Controls the button dimensions and font size.</p>
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Loading State
   *
   * <p>When true, displays a loading spinner and disables interaction.</p>
   * @default false
   */
  loading?: boolean;

  /**
   * Disabled State
   *
   * <p>When true, the button is non-interactive and visually dimmed.</p>
   * @default false
   */
  disabled?: boolean;

  /**
   * Full Width Mode
   *
   * <p>When true, the button expands to fill its container width.</p>
   * @default false
   */
  fullWidth?: boolean;

  /**
   * Start Icon Name
   *
   * <p>Icon name to display before the button text.
   * The actual icon is resolved by the Icon component from UIAdapter.</p>
   */
  startIcon?: string;

  /**
   * End Icon Name
   *
   * <p>Icon name to display after the button text.
   * The actual icon is resolved by the Icon component from UIAdapter.</p>
   */
  endIcon?: string;

  /**
   * Click Event Handler
   *
   * <p>Callback fired when the button is clicked.</p>
   */
  onClick?: (event: MouseEvent<HTMLButtonElement>) => void;

  /**
   * HTML Button Type
   *
   * <p>Native HTML button type attribute for form integration.</p>
   * @default 'button'
   */
  type?: 'button' | 'submit' | 'reset';

  /**
   * Custom Inline Styles
   *
   * <p>CSS properties applied directly to the button element.</p>
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   *
   * <p>Additional CSS class names for styling customization.</p>
   */
  className?: string;

  /**
   * Button Content
   *
   * <p>The text or elements displayed inside the button.</p>
   */
  children: ReactNode;
}

// ============================================================================
// Input Component
// ============================================================================

/**
 * Input Field Type
 *
 * <p>HTML input type attribute values supported by the Input component.</p>
 */
export type InputType = 'text' | 'password' | 'email' | 'number' | 'tel' | 'url' | 'search';

/**
 * Input Component Props
 *
 * <p>UI library agnostic input field properties. Supports all standard
 * text input use cases with validation and accessibility features.</p>
 *
 * @example
 * ```tsx
 * <Input
 *   label="Email"
 *   type="email"
 *   placeholder="Enter your email"
 *   value={email}
 *   onChange={(e) => setEmail(e.target.value)}
 *   error={!isValidEmail}
 *   helperText={!isValidEmail ? 'Invalid email format' : ''}
 * />
 * ```
 */
export interface InputProps {
  /**
   * Input Field Type
   *
   * <p>Determines the input behavior and keyboard on mobile devices.</p>
   * @default 'text'
   */
  type?: InputType;

  /**
   * Current Value
   *
   * <p>The controlled value of the input field.</p>
   */
  value?: string;

  /**
   * Default Value
   *
   * <p>The initial value for uncontrolled input usage.</p>
   */
  defaultValue?: string;

  /**
   * Placeholder Text
   *
   * <p>Hint text displayed when the input is empty.</p>
   */
  placeholder?: string;

  /**
   * Field Label
   *
   * <p>Accessible label displayed above or beside the input.</p>
   */
  label?: string;

  /**
   * Helper Text
   *
   * <p>Descriptive text displayed below the input for guidance.</p>
   */
  helperText?: string;

  /**
   * Error State
   *
   * <p>When true, displays the input in an error state with visual feedback.</p>
   * @default false
   */
  error?: boolean;

  /**
   * Disabled State
   *
   * <p>When true, the input is non-interactive and visually dimmed.</p>
   * @default false
   */
  disabled?: boolean;

  /**
   * Read-Only State
   *
   * <p>When true, the input value can be selected but not modified.</p>
   * @default false
   */
  readOnly?: boolean;

  /**
   * Required Field Indicator
   *
   * <p>When true, displays a required field indicator.</p>
   * @default false
   */
  required?: boolean;

  /**
   * Input Size
   *
   * <p>Controls the input dimensions and font size.</p>
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Full Width Mode
   *
   * <p>When true, the input expands to fill its container width.</p>
   * @default false
   */
  fullWidth?: boolean;

  /**
   * Start Adornment Icon
   *
   * <p>Icon name displayed at the start of the input.</p>
   */
  startAdornment?: string;

  /**
   * End Adornment Icon
   *
   * <p>Icon name displayed at the end of the input.
   * Commonly used for clear button or visibility toggle.</p>
   */
  endAdornment?: string;

  /**
   * Maximum Character Length
   *
   * <p>Maximum number of characters allowed in the input.</p>
   */
  maxLength?: number;

  /**
   * HTML Name Attribute
   *
   * <p>Form field name for form submission.</p>
   */
  name?: string;

  /**
   * Auto Focus
   *
   * <p>When true, the input receives focus on mount.</p>
   * @default false
   */
  autoFocus?: boolean;

  /**
   * Auto Complete Hint
   *
   * <p>Browser autocomplete hint for the input field.</p>
   */
  autoComplete?: string;

  /**
   * Change Event Handler
   *
   * <p>Callback fired when the input value changes.</p>
   */
  onChange?: (event: ChangeEvent<HTMLInputElement>) => void;

  /**
   * Focus Event Handler
   *
   * <p>Callback fired when the input receives focus.</p>
   */
  onFocus?: (event: FocusEvent<HTMLInputElement>) => void;

  /**
   * Blur Event Handler
   *
   * <p>Callback fired when the input loses focus.</p>
   */
  onBlur?: (event: FocusEvent<HTMLInputElement>) => void;

  /**
   * Key Down Event Handler
   *
   * <p>Callback fired on key down events.</p>
   */
  onKeyDown?: (event: KeyboardEvent<HTMLInputElement>) => void;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;
}

// ============================================================================
// Select Component
// ============================================================================

/**
 * Select Option Item
 *
 * <p>Represents a single option in the Select component dropdown.</p>
 */
export interface SelectOption {
  /**
   * Option Value
   *
   * <p>The value submitted when this option is selected.</p>
   */
  value: string | number;

  /**
   * Display Label
   *
   * <p>The text displayed for this option.</p>
   */
  label: string;

  /**
   * Disabled State
   *
   * <p>When true, the option cannot be selected.</p>
   */
  disabled?: boolean;
}

/**
 * Select Component Props
 *
 * <p>UI library agnostic select/dropdown properties. Supports single
 * and multiple selection modes with search capability.</p>
 *
 * @example
 * ```tsx
 * <Select
 *   label="Country"
 *   options={countries}
 *   value={selectedCountry}
 *   onChange={setSelectedCountry}
 *   searchable
 * />
 * ```
 */
export interface SelectProps {
  /**
   * Available Options
   *
   * <p>Array of selectable options.</p>
   */
  options: SelectOption[];

  /**
   * Current Value
   *
   * <p>The currently selected value(s). Array for multiple selection mode.</p>
   */
  value?: string | number | Array<string | number>;

  /**
   * Default Value
   *
   * <p>Initial value for uncontrolled usage.</p>
   */
  defaultValue?: string | number | Array<string | number>;

  /**
   * Multiple Selection Mode
   *
   * <p>When true, allows selecting multiple options.</p>
   * @default false
   */
  multiple?: boolean;

  /**
   * Searchable Mode
   *
   * <p>When true, enables search/filter functionality in the dropdown.</p>
   * @default false
   */
  searchable?: boolean;

  /**
   * Field Label
   */
  label?: string;

  /**
   * Placeholder Text
   *
   * <p>Text displayed when no option is selected.</p>
   */
  placeholder?: string;

  /**
   * Helper Text
   */
  helperText?: string;

  /**
   * Error State
   */
  error?: boolean;

  /**
   * Disabled State
   */
  disabled?: boolean;

  /**
   * Required Indicator
   */
  required?: boolean;

  /**
   * Component Size
   */
  size?: ComponentSize;

  /**
   * Full Width Mode
   */
  fullWidth?: boolean;

  /**
   * Clearable Mode
   *
   * <p>When true, displays a clear button to reset selection.</p>
   * @default false
   */
  clearable?: boolean;

  /**
   * HTML Name Attribute
   */
  name?: string;

  /**
   * Change Event Handler
   *
   * <p>Callback fired when selection changes.
   * The value type depends on multiple mode.</p>
   */
  onChange?: (value: string | number | Array<string | number>) => void;

  /**
   * Search Event Handler
   *
   * <p>Callback fired when search input changes (only in searchable mode).</p>
   */
  onSearch?: (searchText: string) => void;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;
}

// ============================================================================
// Card Component
// ============================================================================

/**
 * Card Component Props
 *
 * <p>Container component for grouping related content with optional
 * elevation and interactive states.</p>
 *
 * @example
 * ```tsx
 * <Card
 *   title="User Profile"
 *   hoverable
 *   onClick={handleCardClick}
 * >
 *   <p>Card content here</p>
 * </Card>
 * ```
 */
export interface CardProps {
  /**
   * Card Title
   *
   * <p>Optional title displayed at the top of the card.</p>
   */
  title?: ReactNode;

  /**
   * Card Subtitle
   *
   * <p>Optional subtitle displayed below the title.</p>
   */
  subtitle?: ReactNode;

  /**
   * Elevation Level
   *
   * <p>Shadow depth level. 0 means no shadow.</p>
   * @default 1
   */
  elevation?: number;

  /**
   * Hoverable State
   *
   * <p>When true, the card shows hover effects on mouse over.</p>
   * @default false
   */
  hoverable?: boolean;

  /**
   * Bordered Style
   *
   * <p>When true, displays a border instead of/in addition to shadow.</p>
   * @default false
   */
  bordered?: boolean;

  /**
   * Click Event Handler
   *
   * <p>Callback fired when the card is clicked.</p>
   */
  onClick?: (event: MouseEvent<HTMLDivElement>) => void;

  /**
   * Header Actions
   *
   * <p>Action elements displayed in the card header area.</p>
   */
  headerActions?: ReactNode;

  /**
   * Footer Content
   *
   * <p>Content displayed in the card footer area.</p>
   */
  footer?: ReactNode;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;

  /**
   * Card Content
   */
  children?: ReactNode;
}

// ============================================================================
// Avatar Component
// ============================================================================

/**
 * Avatar Shape Variants
 */
export type AvatarShape = 'circle' | 'square' | 'rounded';

/**
 * Avatar Component Props
 *
 * <p>User avatar or icon display component with various presentation options.</p>
 *
 * @example
 * ```tsx
 * <Avatar
 *   src={user.avatarUrl}
 *   alt={user.name}
 *   size="large"
 *   fallback={user.initials}
 * />
 * ```
 */
export interface AvatarProps {
  /**
   * Image Source URL
   *
   * <p>URL of the avatar image.</p>
   */
  src?: string;

  /**
   * Alt Text
   *
   * <p>Alternative text for accessibility.</p>
   */
  alt?: string;

  /**
   * Avatar Size
   *
   * <p>Predefined size or custom pixel value.</p>
   * @default 'medium'
   */
  size?: ComponentSize | number;

  /**
   * Avatar Shape
   *
   * @default 'circle'
   */
  shape?: AvatarShape;

  /**
   * Fallback Content
   *
   * <p>Content displayed when image fails to load (e.g., user initials).</p>
   */
  fallback?: ReactNode;

  /**
   * Icon Name
   *
   * <p>Icon to display when no image or fallback is provided.</p>
   */
  icon?: string;

  /**
   * Background Color
   *
   * <p>Custom background color for the avatar.</p>
   */
  bgColor?: string;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;

  /**
   * Avatar Content
   *
   * <p>Direct content to display (e.g., text initials).</p>
   */
  children?: ReactNode;
}

// ============================================================================
// Badge Component
// ============================================================================

/**
 * Badge Color Variants
 */
export type BadgeColor = 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success';

/**
 * Badge Component Props
 *
 * <p>Small status indicator that can be attached to other elements.</p>
 *
 * @example
 * ```tsx
 * <Badge count={5} color="error" showZero={false}>
 *   <Icon name="notification" />
 * </Badge>
 * ```
 */
export interface BadgeProps {
  /**
   * Badge Count
   *
   * <p>Numeric value to display. If 0 and showZero is false, badge is hidden.</p>
   */
  count?: number;

  /**
   * Maximum Count
   *
   * <p>Maximum count to display. Exceeding values show as "max+".</p>
   * @default 99
   */
  max?: number;

  /**
   * Show Zero
   *
   * <p>When true, displays the badge even when count is 0.</p>
   * @default false
   */
  showZero?: boolean;

  /**
   * Dot Mode
   *
   * <p>When true, displays a simple dot instead of count.</p>
   * @default false
   */
  dot?: boolean;

  /**
   * Badge Color
   *
   * @default 'primary'
   */
  color?: BadgeColor;

  /**
   * Badge Position Offset
   *
   * <p>Offset from the default position [horizontal, vertical].</p>
   */
  offset?: [number, number];

  /**
   * Invisible Mode
   *
   * <p>When true, the badge is not rendered.</p>
   * @default false
   */
  invisible?: boolean;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;

  /**
   * Badge Target Element
   *
   * <p>The element that the badge is attached to.</p>
   */
  children?: ReactNode;
}

// ============================================================================
// Tooltip Component
// ============================================================================

/**
 * Tooltip Placement Options
 */
export type TooltipPlacement =
  | 'top'
  | 'top-start'
  | 'top-end'
  | 'bottom'
  | 'bottom-start'
  | 'bottom-end'
  | 'left'
  | 'left-start'
  | 'left-end'
  | 'right'
  | 'right-start'
  | 'right-end';

/**
 * Tooltip Component Props
 *
 * <p>Informative text that appears on hover or focus.</p>
 *
 * @example
 * ```tsx
 * <Tooltip title="Save changes" placement="top">
 *   <Button startIcon="save">Save</Button>
 * </Tooltip>
 * ```
 */
export interface TooltipProps {
  /**
   * Tooltip Content
   *
   * <p>The content displayed in the tooltip.</p>
   */
  title: ReactNode;

  /**
   * Tooltip Placement
   *
   * <p>Position of the tooltip relative to the target element.</p>
   * @default 'top'
   */
  placement?: TooltipPlacement;

  /**
   * Show Arrow
   *
   * <p>When true, displays an arrow pointing to the target element.</p>
   * @default true
   */
  arrow?: boolean;

  /**
   * Enter Delay (ms)
   *
   * <p>Delay before showing the tooltip.</p>
   * @default 100
   */
  enterDelay?: number;

  /**
   * Leave Delay (ms)
   *
   * <p>Delay before hiding the tooltip.</p>
   * @default 0
   */
  leaveDelay?: number;

  /**
   * Disabled State
   *
   * <p>When true, the tooltip is disabled.</p>
   * @default false
   */
  disabled?: boolean;

  /**
   * Custom Inline Styles
   *
   * <p>Styles applied to the tooltip popup.</p>
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;

  /**
   * Tooltip Target Element
   *
   * <p>The element that triggers the tooltip on hover/focus.</p>
   */
  children: ReactNode;
}

// ============================================================================
// Menu Components (Atomic Level for Shell Layout Assembly)
// ============================================================================

/**
 * Menu Item Definition
 *
 * <p>Represents a navigation item in the menu hierarchy.
 * Used by both the Menu component and Shell layout components.</p>
 */
export interface MenuItem {
  /**
   * Unique Item Key
   *
   * <p>Unique identifier for the menu item. Used for selection tracking.</p>
   */
  key: string;

  /**
   * Display Label
   *
   * <p>Text displayed for the menu item.</p>
   */
  label: string;

  /**
   * Icon Name
   *
   * <p>Icon name resolved by the Icon component.</p>
   */
  icon?: string;

  /**
   * Navigation Path
   *
   * <p>URL path for navigation when the item is clicked.</p>
   */
  path?: string;

  /**
   * Child Menu Items
   *
   * <p>Nested submenu items for hierarchical navigation.</p>
   */
  children?: MenuItem[];

  /**
   * Hidden State
   *
   * <p>When true, the item is not rendered.</p>
   */
  hidden?: boolean;

  /**
   * Sort Order
   *
   * <p>Numeric weight for sorting. Lower values appear first.</p>
   */
  order?: number;

  /**
   * Disabled State
   *
   * <p>When true, the item is non-interactive.</p>
   */
  disabled?: boolean;

  /**
   * Badge Count
   *
   * <p>Optional notification count displayed on the item.</p>
   */
  badge?: number;
}

/**
 * Menu Component Props
 *
 * <p>Atomic navigation menu component. This is a presentation component
 * used by Shell layer to assemble layout components like Sidebar.</p>
 *
 * <p><strong>Architectural Note:</strong> This is an atomic component.
 * Sidebar and Header are assembled at Shell layer using this component.</p>
 *
 * @example
 * ```tsx
 * <Menu
 *   items={menuItems}
 *   selectedKey={currentPath}
 *   onSelect={(key, item) => navigate(item.path)}
 *   collapsed={sidebarCollapsed}
 * />
 * ```
 */
export interface MenuProps {
  /**
   * Menu Items
   *
   * <p>Array of menu items to render.</p>
   */
  items: MenuItem[];

  /**
   * Selected Item Key
   *
   * <p>Key of the currently selected item for highlight.</p>
   */
  selectedKey?: string;

  /**
   * Expanded Keys
   *
   * <p>Keys of expanded submenu items.</p>
   */
  expandedKeys?: string[];

  /**
   * Default Expanded Keys
   *
   * <p>Initially expanded submenu keys for uncontrolled usage.</p>
   */
  defaultExpandedKeys?: string[];

  /**
   * Selection Handler
   *
   * <p>Callback fired when a menu item is selected.</p>
   */
  onSelect?: (key: string, item: MenuItem) => void;

  /**
   * Expand Handler
   *
   * <p>Callback fired when submenu expand state changes.</p>
   */
  onExpand?: (expandedKeys: string[]) => void;

  /**
   * Collapsed Mode
   *
   * <p>When true, displays the menu in collapsed icon-only mode.</p>
   * @default false
   */
  collapsed?: boolean;

  /**
   * Inline Mode
   *
   * <p>When true, submenus expand inline. When false, submenus popup.</p>
   * @default true
   */
  inlineMode?: boolean;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;
}

/**
 * Menu Item Component Props
 *
 * <p>Individual menu item component props. Used for custom menu item rendering.</p>
 */
export interface MenuItemProps {
  /**
   * Item Data
   *
   * <p>The menu item data object.</p>
   */
  item: MenuItem;

  /**
   * Selected State
   *
   * <p>Whether this item is currently selected.</p>
   */
  selected?: boolean;

  /**
   * Depth Level
   *
   * <p>Nesting depth for indentation (0 = root level).</p>
   */
  depth?: number;

  /**
   * Collapsed Mode
   *
   * <p>Whether the parent menu is in collapsed mode.</p>
   */
  collapsed?: boolean;

  /**
   * Click Handler
   */
  onClick?: (event: MouseEvent<HTMLElement>) => void;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;
}

// ============================================================================
// Modal Component
// ============================================================================

/**
 * Modal Size Variants
 */
export type ModalSize = 'small' | 'medium' | 'large' | 'fullscreen';

/**
 * Modal Component Props
 *
 * <p>Dialog/modal component for displaying content in an overlay.</p>
 *
 * @example
 * ```tsx
 * <Modal
 *   open={isOpen}
 *   title="Confirm Action"
 *   onClose={handleClose}
 *   onConfirm={handleConfirm}
 * >
 *   <p>Are you sure you want to proceed?</p>
 * </Modal>
 * ```
 */
export interface ModalProps {
  /**
   * Open State
   *
   * <p>Whether the modal is visible.</p>
   */
  open: boolean;

  /**
   * Modal Title
   */
  title?: ReactNode;

  /**
   * Modal Size
   *
   * @default 'medium'
   */
  size?: ModalSize;

  /**
   * Close on Overlay Click
   *
   * <p>When true, clicking the overlay backdrop closes the modal.</p>
   * @default true
   */
  closeOnOverlayClick?: boolean;

  /**
   * Close on Escape Key
   *
   * <p>When true, pressing Escape closes the modal.</p>
   * @default true
   */
  closeOnEscape?: boolean;

  /**
   * Show Close Button
   *
   * <p>When true, displays a close button in the header.</p>
   * @default true
   */
  showCloseButton?: boolean;

  /**
   * Centered Position
   *
   * <p>When true, centers the modal vertically.</p>
   * @default true
   */
  centered?: boolean;

  /**
   * Custom Width
   *
   * <p>Custom width override (CSS value).</p>
   */
  width?: string | number;

  /**
   * Footer Content
   *
   * <p>Custom footer content. Set to null to hide footer.</p>
   */
  footer?: ReactNode;

  /**
   * Confirm Button Text
   *
   * <p>Text for the default confirm button.</p>
   * @default 'OK'
   */
  confirmText?: string;

  /**
   * Cancel Button Text
   *
   * <p>Text for the default cancel button.</p>
   * @default 'Cancel'
   */
  cancelText?: string;

  /**
   * Confirm Button Loading
   *
   * <p>When true, the confirm button shows loading state.</p>
   * @default false
   */
  confirmLoading?: boolean;

  /**
   * Close Handler
   *
   * <p>Callback fired when the modal requests to be closed.</p>
   */
  onClose: () => void;

  /**
   * Confirm Handler
   *
   * <p>Callback fired when the confirm button is clicked.</p>
   */
  onConfirm?: () => void;

  /**
   * Cancel Handler
   *
   * <p>Callback fired when the cancel button is clicked.</p>
   */
  onCancel?: () => void;

  /**
   * After Open Handler
   *
   * <p>Callback fired after the modal has opened.</p>
   */
  afterOpen?: () => void;

  /**
   * After Close Handler
   *
   * <p>Callback fired after the modal has closed.</p>
   */
  afterClose?: () => void;

  /**
   * Custom Inline Styles
   *
   * <p>Styles applied to the modal content container.</p>
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;

  /**
   * Modal Content
   */
  children?: ReactNode;
}

// ============================================================================
// Message API (Toast/Snackbar)
// ============================================================================

/**
 * Message Type Variants
 */
export type MessageType = 'success' | 'error' | 'warning' | 'info' | 'loading';

/**
 * Message Configuration Options
 */
export interface MessageOptions {
  /**
   * Message Content
   */
  content: ReactNode;

  /**
   * Display Duration (ms)
   *
   * <p>Duration before auto-dismiss. 0 means manual dismiss only.</p>
   * @default 3000
   */
  duration?: number;

  /**
   * Closable
   *
   * <p>When true, displays a close button.</p>
   * @default false
   */
  closable?: boolean;

  /**
   * Unique Key
   *
   * <p>Unique key for updating or closing specific messages.</p>
   */
  key?: string;

  /**
   * Close Callback
   *
   * <p>Callback fired when the message is closed.</p>
   */
  onClose?: () => void;
}

/**
 * Message Destroy Function
 *
 * <p>Function returned by message calls to manually dismiss the message.</p>
 */
export type MessageDestroy = () => void;

/**
 * Message API Interface
 *
 * <p>Imperative API for displaying toast/snackbar messages.
 * This is a stateless API - implementations manage message state internally.</p>
 *
 * @example
 * ```tsx
 * // Using the message API from UIAdapter
 * const { message } = useUI();
 *
 * message.success({ content: 'Saved successfully!' });
 * message.error({ content: 'Operation failed', duration: 5000 });
 *
 * // With loading state
 * const destroy = message.loading({ content: 'Processing...' });
 * await doAsyncOperation();
 * destroy();
 * message.success({ content: 'Done!' });
 * ```
 */
export interface MessageAPI {
  /**
   * Success Message
   */
  success: (options: MessageOptions | string) => MessageDestroy;

  /**
   * Error Message
   */
  error: (options: MessageOptions | string) => MessageDestroy;

  /**
   * Warning Message
   */
  warning: (options: MessageOptions | string) => MessageDestroy;

  /**
   * Info Message
   */
  info: (options: MessageOptions | string) => MessageDestroy;

  /**
   * Loading Message
   */
  loading: (options: MessageOptions | string) => MessageDestroy;

  /**
   * Destroy Specific Message
   *
   * <p>Destroys a message by its key.</p>
   */
  destroy: (key?: string) => void;

  /**
   * Destroy All Messages
   */
  destroyAll: () => void;
}

// ============================================================================
// Theme System
// ============================================================================

/**
 * Theme Tokens (MUI Standard Color Palette)
 *
 * <p>Comprehensive design tokens following Material UI conventions.
 * These tokens ensure visual consistency across different UI adapter implementations.</p>
 *
 * <p><strong>Architectural Note:</strong> Layout-specific colors (sidebarBackground, headerBackground, etc.)
 * are included to support Shell layer layout assembly while maintaining theme consistency.</p>
 */
export interface ThemeTokens {
  // ========================================
  // Brand Colors (Primary & Secondary)
  // ========================================

  /**
   * Primary Brand Color
   *
   * <p>Main brand color for primary actions and highlights.</p>
   */
  primary: string;

  /**
   * Primary Color - Light Variant
   *
   * <p>Lighter shade of primary for hover states and backgrounds.</p>
   */
  primaryLight: string;

  /**
   * Primary Color - Dark Variant
   *
   * <p>Darker shade of primary for active states.</p>
   */
  primaryDark: string;

  /**
   * Primary Contrast Text
   *
   * <p>Text color that contrasts with primary background.</p>
   */
  primaryContrastText: string;

  /**
   * Secondary Brand Color
   */
  secondary: string;

  /**
   * Secondary Color - Light Variant
   */
  secondaryLight: string;

  /**
   * Secondary Color - Dark Variant
   */
  secondaryDark: string;

  /**
   * Secondary Contrast Text
   */
  secondaryContrastText: string;

  // ========================================
  // Semantic Colors
  // ========================================

  /**
   * Error/Danger Color
   *
   * <p>Used for error states, destructive actions, invalid inputs.</p>
   */
  error: string;

  /**
   * Warning Color
   *
   * <p>Used for warning messages and caution states.</p>
   */
  warning: string;

  /**
   * Info Color
   *
   * <p>Used for informational messages and neutral highlights.</p>
   */
  info: string;

  /**
   * Success Color
   *
   * <p>Used for success states and positive confirmations.</p>
   */
  success: string;

  // ========================================
  // Neutral Colors
  // ========================================

  /**
   * Page Background Color
   *
   * <p>Default background color for the page/viewport.</p>
   */
  background: string;

  /**
   * Paper/Surface Color
   *
   * <p>Background color for elevated surfaces like cards and dialogs.</p>
   */
  paper: string;

  /**
   * Primary Text Color
   *
   * <p>Main text color for headings and body text.</p>
   */
  textPrimary: string;

  /**
   * Secondary Text Color
   *
   * <p>Subdued text color for captions and supporting text.</p>
   */
  textSecondary: string;

  /**
   * Disabled Text Color
   *
   * <p>Text color for disabled/inactive elements.</p>
   */
  textDisabled: string;

  /**
   * Divider Color
   *
   * <p>Color for divider lines and borders.</p>
   */
  divider: string;

  // ========================================
  // Layout Colors (Shell Layer Support)
  // ========================================

  /**
   * Sidebar Background Color
   *
   * <p>Background color for the sidebar navigation area.</p>
   */
  sidebarBackground: string;

  /**
   * Sidebar Text Color
   *
   * <p>Default text color in the sidebar.</p>
   */
  sidebarText: string;

  /**
   * Sidebar Active Item Background
   *
   * <p>Background color for the selected/active menu item.</p>
   */
  sidebarActiveBackground: string;

  /**
   * Sidebar Hover Background
   *
   * <p>Background color on hover for sidebar items.</p>
   */
  sidebarHoverBackground: string;

  /**
   * Header Background Color
   *
   * <p>Background color for the top header area.</p>
   */
  headerBackground: string;

  /**
   * Header Text Color
   *
   * <p>Text color in the header area.</p>
   */
  headerText: string;

  // ========================================
  // Shape Tokens
  // ========================================

  /**
   * Border Radius - Small
   *
   * <p>Small border radius for compact elements (e.g., chips, tags).</p>
   */
  borderRadiusSmall: number;

  /**
   * Border Radius - Medium
   *
   * <p>Default border radius for buttons, inputs, cards.</p>
   */
  borderRadiusMedium: number;

  /**
   * Border Radius - Large
   *
   * <p>Large border radius for modals, large cards.</p>
   */
  borderRadiusLarge: number;
}

/**
 * Theme Provider Component Props
 *
 * <p>Provides theme context to child components.
 * Implemented by each UI adapter to apply the appropriate theme.</p>
 */
export interface ThemeProviderProps {
  /**
   * Child Elements
   *
   * <p>Components that receive theme context.</p>
   */
  children: ReactNode;

  /**
   * Theme Mode
   *
   * <p>Light or dark theme mode. Defaults to light.</p>
   * @default 'light'
   */
  theme?: 'light' | 'dark';
}

// ============================================================================
// Icon System
// ============================================================================

/**
 * Icon Component Props
 *
 * <p>Name-based icon lookup component. The actual icon rendering
 * is handled by the UI adapter implementation (MUI icons, SVG icons, etc.).</p>
 *
 * @example
 * ```tsx
 * <Icon name="dashboard" size="medium" color="#1976d2" />
 * <Icon name="settings" size={24} />
 * ```
 */
export interface IconProps {
  /**
   * Icon Name
   *
   * <p>Name/identifier of the icon to display.
   * The name is resolved by the UI adapter's icon mapping.</p>
   */
  name: string;

  /**
   * Icon Size
   *
   * <p>Predefined size or custom pixel value.</p>
   * @default 'medium'
   */
  size?: ComponentSize | number;

  /**
   * Icon Color
   *
   * <p>Custom color override (CSS color value).</p>
   */
  color?: string;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;

  /**
   * Accessible Label
   *
   * <p>ARIA label for screen readers.</p>
   */
  'aria-label'?: string;

  /**
   * Click Handler
   *
   * <p>Optional click handler for interactive icons.</p>
   */
  onClick?: (event: MouseEvent<HTMLElement>) => void;
}

// ============================================================================
// UIAdapter Interface (Core Contract)
// ============================================================================

/**
 * UI Adapter Interface
 *
 * <p>Defines the contract for UI library implementations. All UI adapters
 * (MUI, Ant Design, Native CSS) must implement this interface.</p>
 *
 * <p><strong>Architectural Constraints (v3.0.4 Blueprint):</strong></p>
 * <ul>
 *   <li>This interface only contains ATOMIC components</li>
 *   <li>Layout components (Sidebar, Header, Layout) are FORBIDDEN here</li>
 *   <li>Shell layer assembles layouts using these atomic components</li>
 *   <li>Host layer selects adapter via configuration</li>
 * </ul>
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
 *   getThemeTokens: () => MUI_THEME_TOKENS,
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
   * <p>Primary action trigger component.</p>
   */
  Button: FC<ButtonProps>;

  /**
   * Input Component
   *
   * <p>Text input field component.</p>
   */
  Input: FC<InputProps>;

  /**
   * Select Component
   *
   * <p>Dropdown selection component.</p>
   */
  Select: FC<SelectProps>;

  // ========================================
  // Display Components
  // ========================================

  /**
   * Card Component
   *
   * <p>Content container component.</p>
   */
  Card: FC<CardProps>;

  /**
   * Avatar Component
   *
   * <p>User avatar display component.</p>
   */
  Avatar: FC<AvatarProps>;

  /**
   * Badge Component
   *
   * <p>Status indicator component.</p>
   */
  Badge: FC<BadgeProps>;

  /**
   * Tooltip Component
   *
   * <p>Hover information component.</p>
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
   * <p>Navigation menu list component. Used by Shell layer to assemble Sidebar.</p>
   */
  Menu: FC<MenuProps>;

  /**
   * Menu Item Component
   *
   * <p>Single menu item for custom rendering scenarios.</p>
   */
  MenuItem: FC<MenuItemProps>;

  // ========================================
  // Feedback Components
  // ========================================

  /**
   * Modal Component
   *
   * <p>Dialog/overlay component.</p>
   */
  Modal: FC<ModalProps>;

  /**
   * Message API
   *
   * <p>Imperative toast/snackbar API.</p>
   */
  message: MessageAPI;

  // ========================================
  // Theme System
  // ========================================

  /**
   * Theme Provider Component
   *
   * <p>Provides theme context to child components.</p>
   */
  ThemeProvider: FC<ThemeProviderProps>;

  /**
   * Get Theme Tokens
   *
   * <p>Returns the current theme tokens for style calculations.</p>
   */
  getThemeTokens: () => ThemeTokens;

  // ========================================
  // Icon System
  // ========================================

  /**
   * Icon Component
   *
   * <p>Name-based icon display component.</p>
   */
  Icon: FC<IconProps>;
}

// ============================================================================
// FORBIDDEN Components in UIAdapter (Architectural Constraint)
// ============================================================================
// ❌ Sidebar - Assembled in Shell layer using Menu + Icon
// ❌ Header  - Assembled in Shell layer using Button + Avatar + Badge
// ❌ Layout  - Assembled in Shell layer using ThemeProvider + structure

// ============================================================================
// UIAdapter Factory & Configuration
// ============================================================================

/**
 * UI Adapter Configuration
 *
 * <p>Configuration options for creating a customized UI adapter instance.</p>
 */
export interface UIAdapterConfig {
  /**
   * Default Theme Mode
   *
   * @default 'light'
   */
  defaultTheme?: 'light' | 'dark';

  /**
   * Primary Color Override
   *
   * <p>Custom primary brand color.</p>
   */
  primaryColor?: string;

  /**
   * Border Radius Override
   *
   * <p>Custom default border radius (pixels).</p>
   */
  borderRadius?: number;

  /**
   * Font Family Override
   *
   * <p>Custom font family for all text.</p>
   */
  fontFamily?: string;
}

/**
 * UI Adapter Factory Function Type
 *
 * <p>Factory function signature for creating UI adapter instances.</p>
 *
 * @example
 * ```typescript
 * export const createMuiAdapter: UIAdapterFactory = (config) => {
 *   return {
 *     Button: MuiButton,
 *     // ... other components
 *   };
 * };
 * ```
 */
export type UIAdapterFactory = (config?: UIAdapterConfig) => UIAdapter;

// ============================================================================
// Default Theme Tokens (MUI Standard)
// ============================================================================

/**
 * MUI Standard Theme Tokens (Light Mode Default)
 *
 * <p>Default theme token values following Material UI design system.
 * These values serve as the baseline for all UI adapter implementations.</p>
 *
 * <p>Usage: Import this constant when creating custom adapters or
 * as a reference for theme customization.</p>
 *
 * @example
 * ```typescript
 * // Custom theme based on MUI defaults
 * const customTokens: ThemeTokens = {
 *   ...MUI_THEME_TOKENS,
 *   primary: '#7c3aed',  // Custom primary color
 * };
 * ```
 */
export const MUI_THEME_TOKENS: ThemeTokens = {
  // Brand Colors
  primary: '#1976d2',
  primaryLight: '#42a5f5',
  primaryDark: '#1565c0',
  primaryContrastText: '#ffffff',

  secondary: '#9c27b0',
  secondaryLight: '#ba68c8',
  secondaryDark: '#7b1fa2',
  secondaryContrastText: '#ffffff',

  // Semantic Colors
  error: '#d32f2f',
  warning: '#ed6c02',
  info: '#0288d1',
  success: '#2e7d32',

  // Neutral Colors
  background: '#f5f5f5',
  paper: '#ffffff',
  textPrimary: 'rgba(0, 0, 0, 0.87)',
  textSecondary: 'rgba(0, 0, 0, 0.6)',
  textDisabled: 'rgba(0, 0, 0, 0.38)',
  divider: 'rgba(0, 0, 0, 0.12)',

  // Layout Colors (Shell Layer Support)
  sidebarBackground: '#1e293b',
  sidebarText: 'rgba(255, 255, 255, 0.87)',
  sidebarActiveBackground: '#1976d2',
  sidebarHoverBackground: 'rgba(255, 255, 255, 0.08)',
  headerBackground: '#ffffff',
  headerText: 'rgba(0, 0, 0, 0.87)',

  // Shape Tokens
  borderRadiusSmall: 4,
  borderRadiusMedium: 8,
  borderRadiusLarge: 12,
};

/**
 * MUI Dark Theme Tokens
 *
 * <p>Dark mode variant of MUI theme tokens.</p>
 */
export const MUI_DARK_THEME_TOKENS: ThemeTokens = {
  // Brand Colors (same as light)
  primary: '#90caf9',
  primaryLight: '#e3f2fd',
  primaryDark: '#42a5f5',
  primaryContrastText: 'rgba(0, 0, 0, 0.87)',

  secondary: '#ce93d8',
  secondaryLight: '#f3e5f5',
  secondaryDark: '#ab47bc',
  secondaryContrastText: 'rgba(0, 0, 0, 0.87)',

  // Semantic Colors (adjusted for dark mode)
  error: '#f44336',
  warning: '#ffa726',
  info: '#29b6f6',
  success: '#66bb6a',

  // Neutral Colors (inverted)
  background: '#121212',
  paper: '#1e1e1e',
  textPrimary: 'rgba(255, 255, 255, 0.87)',
  textSecondary: 'rgba(255, 255, 255, 0.6)',
  textDisabled: 'rgba(255, 255, 255, 0.38)',
  divider: 'rgba(255, 255, 255, 0.12)',

  // Layout Colors (adjusted for dark mode)
  sidebarBackground: '#0f172a',
  sidebarText: 'rgba(255, 255, 255, 0.87)',
  sidebarActiveBackground: '#1e40af',
  sidebarHoverBackground: 'rgba(255, 255, 255, 0.08)',
  headerBackground: '#1e1e1e',
  headerText: 'rgba(255, 255, 255, 0.87)',

  // Shape Tokens (same as light)
  borderRadiusSmall: 4,
  borderRadiusMedium: 8,
  borderRadiusLarge: 12,
};

// ============================================================================
// UI Capability Type (for RuntimeContext.getCapability)
// ============================================================================

/**
 * UI Capability Type Symbol
 *
 * <p>Symbol used to retrieve UIAdapter from RuntimeContext.
 * Use with context.getCapability() to obtain the UI adapter instance.</p>
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
