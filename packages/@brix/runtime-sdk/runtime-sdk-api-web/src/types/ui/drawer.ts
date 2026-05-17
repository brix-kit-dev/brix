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
 * @file Drawer Component Type Definitions
 * @description Defines types for the Drawer panel component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/drawer
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Drawer provides slide-in panel for secondary content
 * - Supports multiple anchor positions (left, right, top, bottom)
 * - Plugins must obtain Drawer through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties } from 'react';

/**
 * Drawer Placement
 *
 * The edge of the screen from which the drawer slides in.
 */
export type DrawerPlacement = 'left' | 'right' | 'top' | 'bottom';

/**
 * Drawer Size Preset
 *
 * Predefined width/height values for the drawer.
 */
export type DrawerSize = 'default' | 'large';

/**
 * Drawer Component Props
 *
 * Slide-in panel component for displaying secondary content or forms.
 * Overlays the main content without navigating away from the current page.
 *
 * **Design Principle: Contextual Navigation**
 * Drawers maintain context by keeping the main page visible (when partially
 * covered) while focusing on detailed content or actions.
 *
 * @example
 * ```tsx
 * const { Drawer, Button, Stack, Input, FormItem } = useUI();
 * const [open, setOpen] = useState(false);
 *
 * // Basic drawer
 * <Button onClick={() => setOpen(true)}>Open Drawer</Button>
 *
 * <Drawer
 *   open={open}
 *   title="User Details"
 *   onClose={() => setOpen(false)}
 * >
 *   <Stack spacing={16}>
 *     <FormItem label="Name">
 *       <Input value={name} onChange={setName} />
 *     </FormItem>
 *   </Stack>
 * </Drawer>
 *
 * // Left-side navigation drawer
 * <Drawer
 *   open={navOpen}
 *   placement="left"
 *   onClose={() => setNavOpen(false)}
 *   closable={false}
 *   width={280}
 * >
 *   <NavigationMenu />
 * </Drawer>
 *
 * // Drawer with footer actions
 * <Drawer
 *   open={editOpen}
 *   title="Edit Item"
 *   onClose={handleClose}
 *   footer={
 *     <Stack direction="row" spacing={8} justify="flex-end">
 *       <Button variant="secondary" onClick={handleClose}>Cancel</Button>
 *       <Button variant="primary" onClick={handleSave}>Save</Button>
 *     </Stack>
 *   }
 * >
 *   <EditForm />
 * </Drawer>
 * ```
 */
export interface DrawerProps {
  /**
   * Open State
   *
   * Whether the drawer is visible.
   */
  open: boolean;

  /**
   * Drawer Title
   *
   * Title displayed in the drawer header.
   */
  title?: ReactNode;

  /**
   * Drawer Placement
   *
   * Edge of the screen from which the drawer slides in.
   * @default 'right'
   */
  placement?: DrawerPlacement;

  /**
   * Drawer Size
   *
   * Preset size for the drawer.
   * Use 'default' for standard width (378px) or 'large' (736px).
   *
   * @default 'default'
   */
  size?: DrawerSize;

  /**
   * Custom Width
   *
   * Custom width for left/right placement.
   * Overrides the size prop.
   */
  width?: number | string;

  /**
   * Custom Height
   *
   * Custom height for top/bottom placement.
   * Overrides the size prop.
   */
  height?: number | string;

  /**
   * Show Close Button
   *
   * When true, displays a close button in the header.
   * @default true
   */
  closable?: boolean;

  /**
   * Close on Mask Click
   *
   * When true, clicking the overlay backdrop closes the drawer.
   * @default true
   */
  maskClosable?: boolean;

  /**
   * Show Mask
   *
   * When true, displays the overlay backdrop.
   * @default true
   */
  mask?: boolean;

  /**
   * Close on Escape
   *
   * When true, pressing Escape closes the drawer.
   * @default true
   */
  keyboard?: boolean;

  /**
   * Push Mode
   *
   * When true, pushes the main content instead of overlaying.
   * Only applies when mask is false.
   *
   * @default false
   */
  push?: boolean;

  /**
   * Z-Index
   *
   * Custom z-index for the drawer.
   * @default 1000
   */
  zIndex?: number;

  /**
   * Extra Header Content
   *
   * Additional content rendered in the header area.
   */
  extra?: ReactNode;

  /**
   * Footer Content
   *
   * Content rendered in the drawer footer.
   * Typically action buttons.
   */
  footer?: ReactNode;

  /**
   * Close Handler
   *
   * Callback fired when the drawer requests to be closed.
   */
  onClose: () => void;

  /**
   * After Open Handler
   *
   * Callback fired after the drawer has fully opened.
   */
  afterOpenChange?: (open: boolean) => void;

  /**
   * Body Styles
   *
   * Styles applied to the drawer body content area.
   */
  bodyStyle?: CSSProperties;

  /**
   * Header Styles
   *
   * Styles applied to the drawer header.
   */
  headerStyle?: CSSProperties;

  /**
   * Content Styles
   *
   * Styles applied to the drawer content wrapper.
   */
  contentStyle?: CSSProperties;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the drawer root.
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   *
   * Additional CSS class names for styling customization.
   */
  className?: string;

  /**
   * Test ID
   *
   * Data attribute for testing frameworks.
   */
  'data-testid'?: string;

  /**
   * Drawer Content
   *
   * The content displayed inside the drawer body.
   */
  children?: ReactNode;
}
