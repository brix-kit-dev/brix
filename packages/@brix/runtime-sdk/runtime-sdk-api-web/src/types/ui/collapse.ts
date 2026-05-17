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
 * @file Collapse Component Type Definitions
 * @description Defines types for the Collapse/Accordion component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/collapse
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Collapse provides expandable content sections
 * - Wraps MUI Accordion / Ant Design Collapse internally
 * - Plugins must obtain Collapse through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 *
 * [Naming Convention]
 * This component is named 'Collapse' to align with Ant Design naming.
 * MUI implementations will wrap Accordion internally.
 */

import type { ReactNode, CSSProperties, Key } from 'react';
import type { ComponentSize } from './common';

/**
 * Collapse Item Definition
 *
 * Configuration object for Collapse items prop.
 * Alternative to using CollapsePanel children.
 */
export interface CollapseItem {
  /**
   * Panel Key
   *
   * Unique identifier for the collapse panel.
   */
  key: string;

  /**
   * Panel Header
   *
   * Content displayed in the panel header/trigger.
   */
  label: ReactNode;

  /**
   * Panel Content
   *
   * Content displayed when the panel is expanded.
   */
  children?: ReactNode;

  /**
   * Disabled State
   *
   * When true, the panel cannot be expanded/collapsed.
   * @default false
   */
  disabled?: boolean;

  /**
   * Show Arrow
   *
   * When true, displays the expand/collapse arrow.
   * @default true
   */
  showArrow?: boolean;

  /**
   * Extra Header Content
   *
   * Additional content rendered at the end of the header.
   * Useful for action buttons or status indicators.
   */
  extra?: ReactNode;

  /**
   * Force Render
   *
   * When true, content is rendered even when collapsed.
   * @default false
   */
  forceRender?: boolean;

  /**
   * Collapsible Trigger
   *
   * Which part of the header triggers collapse.
   * - header: Entire header is clickable
   * - icon: Only the arrow icon is clickable
   * - disabled: Panel cannot be toggled
   *
   * @default 'header'
   */
  collapsible?: 'header' | 'icon' | 'disabled';
}

/**
 * Collapse Expand Icon Position
 *
 * Position of the expand/collapse arrow icon.
 */
export type ExpandIconPosition = 'start' | 'end';

/**
 * Collapse Component Props
 *
 * Expandable content sections component.
 * Used for FAQs, settings groups, or progressive disclosure.
 *
 * **Design Principle: Progressive Disclosure**
 * Collapse components reduce visual complexity by hiding detailed
 * content until explicitly requested by the user.
 *
 * @example
 * ```tsx
 * const { Collapse, CollapsePanel, Typography } = useUI();
 * const [activeKeys, setActiveKeys] = useState(['1']);
 *
 * // Using items prop (recommended)
 * <Collapse
 *   activeKey={activeKeys}
 *   onChange={setActiveKeys}
 *   items={[
 *     { key: '1', label: 'Section 1', children: <Content1 /> },
 *     { key: '2', label: 'Section 2', children: <Content2 /> },
 *     { key: '3', label: 'Section 3', children: <Content3 />, disabled: true },
 *   ]}
 * />
 *
 * // Accordion mode (only one panel open)
 * <Collapse
 *   accordion
 *   items={faqItems.map(faq => ({
 *     key: faq.id,
 *     label: faq.question,
 *     children: <Typography>{faq.answer}</Typography>
 *   }))}
 * />
 *
 * // Using CollapsePanel children
 * <Collapse activeKey={activeKeys} onChange={setActiveKeys}>
 *   <CollapsePanel header="General Settings" key="general">
 *     <GeneralSettings />
 *   </CollapsePanel>
 *   <CollapsePanel header="Advanced Settings" key="advanced">
 *     <AdvancedSettings />
 *   </CollapsePanel>
 * </Collapse>
 *
 * // Borderless ghost style
 * <Collapse
 *   ghost
 *   items={sections}
 *   expandIconPosition="end"
 * />
 * ```
 */
export interface CollapseProps {
  /**
   * Active Panel Keys
   *
   * Keys of currently expanded panels.
   * Use with onChange for controlled mode.
   * Can be string for single key or array for multiple.
   */
  activeKey?: string | string[];

  /**
   * Default Active Keys
   *
   * Initial expanded panel keys for uncontrolled mode.
   */
  defaultActiveKey?: string | string[];

  /**
   * Collapse Items
   *
   * Array of panel definitions.
   * Recommended over CollapsePanel children for better type safety.
   */
  items?: CollapseItem[];

  /**
   * Accordion Mode
   *
   * When true, only one panel can be expanded at a time.
   * @default false
   */
  accordion?: boolean;

  /**
   * Bordered Style
   *
   * When true, renders with borders.
   * @default true
   */
  bordered?: boolean;

  /**
   * Ghost Style
   *
   * When true, renders without borders or background.
   * @default false
   */
  ghost?: boolean;

  /**
   * Collapse Size
   *
   * Size of the collapse panels.
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Expand Icon Position
   *
   * Position of the expand/collapse arrow icon.
   * @default 'start'
   */
  expandIconPosition?: ExpandIconPosition;

  /**
   * Collapsible Trigger
   *
   * Default collapsible trigger for all panels.
   * @default 'header'
   */
  collapsible?: 'header' | 'icon' | 'disabled';

  /**
   * Destroy Inactive Panels
   *
   * When true, unmounts collapsed panel content.
   * @default false
   */
  destroyInactivePanel?: boolean;

  /**
   * Change Handler
   *
   * Callback fired when expanded panels change.
   *
   * @param key - The new active key(s)
   */
  onChange?: (key: string | string[]) => void;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the collapse container.
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
   * CollapsePanel Children
   *
   * CollapsePanel components when not using items prop.
   */
  children?: ReactNode;
}

/**
 * CollapsePanel Component Props
 *
 * Individual expandable panel within a Collapse component.
 * Used as children of Collapse component.
 *
 * @example
 * ```tsx
 * <Collapse>
 *   <CollapsePanel
 *     header="Panel Title"
 *     key="1"
 *     extra={<Tag color="success">Active</Tag>}
 *   >
 *     Panel content here
 *   </CollapsePanel>
 * </Collapse>
 * ```
 */
export interface CollapsePanelProps {
  /**
   * Panel Key
   *
   * Unique identifier for the panel.
   * Required for Collapse to track expanded state.
   */
  key: Key;

  /**
   * Panel Header
   *
   * Content displayed in the panel header/trigger.
   */
  header: ReactNode;

  /**
   * Disabled State
   *
   * When true, the panel cannot be expanded/collapsed.
   * @default false
   */
  disabled?: boolean;

  /**
   * Show Arrow
   *
   * When true, displays the expand/collapse arrow.
   * @default true
   */
  showArrow?: boolean;

  /**
   * Extra Header Content
   *
   * Additional content rendered at the end of the header.
   */
  extra?: ReactNode;

  /**
   * Force Render
   *
   * When true, content is rendered even when collapsed.
   * @default false
   */
  forceRender?: boolean;

  /**
   * Collapsible Trigger
   *
   * Which part of the header triggers collapse.
   */
  collapsible?: 'header' | 'icon' | 'disabled';

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the panel.
   */
  style?: CSSProperties;

  /**
   * Header Styles
   *
   * CSS properties applied to the panel header.
   */
  headerStyle?: CSSProperties;

  /**
   * Custom CSS Class Name
   *
   * Additional CSS class names for styling customization.
   */
  className?: string;

  /**
   * Panel Content
   *
   * Content displayed when the panel is expanded.
   */
  children?: ReactNode;
}
