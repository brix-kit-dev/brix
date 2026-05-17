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
 * @file Tabs Component Type Definitions
 * @description Defines types for the Tabs navigation component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/tabs
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Tabs provides tabbed navigation for switching between views
 * - Supports both declarative TabPane children and items array
 * - Plugins must obtain Tabs through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties } from 'react';
import type { ComponentSize } from './common';

/**
 * Tab Position
 *
 * Position of the tab bar relative to content.
 */
export type TabPosition = 'top' | 'right' | 'bottom' | 'left';

/**
 * Tab Type
 *
 * Visual style variants for tabs.
 * - line: Underlined tabs (default)
 * - card: Card-style tabs with borders
 * - editable-card: Card tabs that can be added/removed
 */
export type TabType = 'line' | 'card' | 'editable-card';

/**
 * Tab Item Definition
 *
 * Configuration object for Tabs items prop.
 * Alternative to using TabPane children.
 */
export interface TabItem {
  /**
   * Tab Key
   *
   * Unique identifier for the tab.
   */
  key: string;

  /**
   * Tab Label
   *
   * Content displayed in the tab header.
   */
  label: ReactNode;

  /**
   * Tab Content
   *
   * Content displayed when the tab is active.
   */
  children?: ReactNode;

  /**
   * Disabled State
   *
   * When true, the tab cannot be selected.
   * @default false
   */
  disabled?: boolean;

  /**
   * Closable Flag
   *
   * When true, shows a close button on the tab.
   * Only applies when type is 'editable-card'.
   *
   * @default true
   */
  closable?: boolean;

  /**
   * Tab Icon
   *
   * Icon name displayed before the label.
   */
  icon?: string;

  /**
   * Force Render
   *
   * When true, the content is rendered even when not active.
   * Use for tabs that need to maintain state.
   *
   * @default false
   */
  forceRender?: boolean;
}

/**
 * Tabs Component Props
 *
 * Tabbed navigation component for organizing content into switchable panels.
 * Supports controlled and uncontrolled modes.
 *
 * **Design Principle: Content Organization**
 * Tabs help organize related content into distinct views,
 * reducing visual complexity while maintaining easy access.
 *
 * @example
 * ```tsx
 * const { Tabs, TabPane } = useUI();
 * const [activeKey, setActiveKey] = useState('1');
 *
 * // Using items prop (recommended)
 * <Tabs
 *   activeKey={activeKey}
 *   onChange={setActiveKey}
 *   items={[
 *     { key: '1', label: 'Tab 1', children: <Content1 /> },
 *     { key: '2', label: 'Tab 2', children: <Content2 /> },
 *     { key: '3', label: 'Tab 3', children: <Content3 />, disabled: true },
 *   ]}
 * />
 *
 * // Using TabPane children
 * <Tabs activeKey={activeKey} onChange={setActiveKey}>
 *   <TabPane tab="Overview" key="overview">
 *     <OverviewContent />
 *   </TabPane>
 *   <TabPane tab="Details" key="details">
 *     <DetailsContent />
 *   </TabPane>
 * </Tabs>
 *
 * // Editable tabs with add/remove
 * <Tabs
 *   type="editable-card"
 *   items={tabs}
 *   activeKey={activeKey}
 *   onChange={setActiveKey}
 *   onEdit={handleEdit}
 * />
 *
 * // Vertical tabs
 * <Tabs
 *   tabPosition="left"
 *   items={categories.map(cat => ({
 *     key: cat.id,
 *     label: cat.name,
 *     icon: cat.icon,
 *     children: <CategoryContent category={cat} />
 *   }))}
 * />
 * ```
 */
export interface TabsProps {
  /**
   * Active Tab Key
   *
   * The key of the currently active tab.
   * Use with onChange for controlled mode.
   */
  activeKey?: string;

  /**
   * Default Active Key
   *
   * Initial active tab key for uncontrolled mode.
   */
  defaultActiveKey?: string;

  /**
   * Tab Items
   *
   * Array of tab definitions.
   * Recommended over TabPane children for better type safety.
   */
  items?: TabItem[];

  /**
   * Tab Type
   *
   * Visual style of the tabs.
   * @default 'line'
   */
  type?: TabType;

  /**
   * Tab Position
   *
   * Position of the tab bar relative to content.
   * @default 'top'
   */
  tabPosition?: TabPosition;

  /**
   * Tab Size
   *
   * Size of the tab bar.
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Centered Tabs
   *
   * When true, centers the tab bar.
   * @default false
   */
  centered?: boolean;

  /**
   * Destroy Inactive Tab Panes
   *
   * When true, unmounts inactive tab content.
   * When false, hides inactive content with CSS.
   *
   * @default false
   */
  destroyInactiveTabPane?: boolean;

  /**
   * Tab Bar Extra Content
   *
   * Extra content rendered in the tab bar.
   * Can specify left or right position.
   */
  tabBarExtraContent?: ReactNode | { left?: ReactNode; right?: ReactNode };

  /**
   * Tab Bar Gap
   *
   * Gap between tabs in pixels.
   */
  tabBarGutter?: number;

  /**
   * Tab Change Handler
   *
   * Callback fired when the active tab changes.
   *
   * @param activeKey - The new active tab key
   */
  onChange?: (activeKey: string) => void;

  /**
   * Tab Edit Handler
   *
   * Callback fired when tabs are added or removed.
   * Only triggered when type is 'editable-card'.
   *
   * @param targetKey - Key of the affected tab
   * @param action - 'add' or 'remove'
   */
  onEdit?: (targetKey: string, action: 'add' | 'remove') => void;

  /**
   * Tab Click Handler
   *
   * Callback fired when a tab is clicked.
   *
   * @param key - The clicked tab key
   */
  onTabClick?: (key: string) => void;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the tabs container.
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
   * TabPane Children
   *
   * TabPane components when not using items prop.
   */
  children?: ReactNode;
}

/**
 * TabPane Component Props
 *
 * Individual tab panel content container.
 * Used as children of Tabs component.
 *
 * @example
 * ```tsx
 * <Tabs>
 *   <TabPane tab="First" key="1">
 *     First tab content
 *   </TabPane>
 *   <TabPane tab={<><Icon name="settings" /> Settings</>} key="2">
 *     Settings content
 *   </TabPane>
 * </Tabs>
 * ```
 */
export interface TabPaneProps {
  /**
   * Tab Key
   *
   * Unique identifier for the tab.
   * Required for Tabs to track active state.
   */
  key: string;

  /**
   * Tab Label
   *
   * Content displayed in the tab header.
   */
  tab: ReactNode;

  /**
   * Disabled State
   *
   * When true, the tab cannot be selected.
   * @default false
   */
  disabled?: boolean;

  /**
   * Closable Flag
   *
   * When true, shows a close button on the tab.
   * Only applies when parent Tabs type is 'editable-card'.
   *
   * @default true
   */
  closable?: boolean;

  /**
   * Force Render
   *
   * When true, content is rendered even when not active.
   * @default false
   */
  forceRender?: boolean;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the tab pane container.
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   *
   * Additional CSS class names for styling customization.
   */
  className?: string;

  /**
   * Tab Content
   *
   * Content displayed when the tab is active.
   */
  children?: ReactNode;
}
