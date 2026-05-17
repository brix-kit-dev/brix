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
 * @file Native Collapse Component
 * @description Pure CSS implementation of CollapseProps and CollapsePanelProps from UIAdapter.
 *              Collapsible content panels (accordion pattern).
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeCollapse
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Accordion mode (single open) or multiple panels open
 * - Smooth CSS transitions for expand/collapse
 * - Customizable expand icon and position
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic container component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for collapsible sections.
 * Replaces direct MUI Accordion usage in enterprise-solutions plugins.
 */

import type { FC, CSSProperties, ReactNode, ReactElement } from 'react';
import { useState, useCallback, Children, isValidElement, useRef, useEffect } from 'react';
import type { CollapseProps, CollapsePanelProps } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// CollapsePanel Component
// ============================================================================

/**
 * Native CollapsePanel Component
 *
 * <p>Individual panel within a Collapse container.
 * Displays a header and collapsible content.</p>
 *
 * @example
 * ```tsx
 * <CollapsePanel header="Section 1" key="1">
 *   Content for section 1
 * </CollapsePanel>
 * ```
 *
 * @param props - CollapsePanelProps from UIAdapter contract
 * @returns Native CollapsePanel component
 */
export const NativeCollapsePanel: FC<CollapsePanelProps & {
  isActive?: boolean;
  onToggle?: () => void;
  expandIconPosition?: 'start' | 'end';
  expandIcon?: ReactNode | ((isActive: boolean) => ReactNode);
  bordered?: boolean;
  size?: 'small' | 'medium' | 'large';
}> = ({
  header,
  extra,
  disabled = false,
  showArrow = true,
  forceRender = false,
  collapsible,
  isActive = false,
  onToggle,
  expandIconPosition = 'start',
  expandIcon,
  bordered = true,
  size = 'medium',
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Content ref for measuring height
  const contentRef = useRef<HTMLDivElement>(null);
  const [contentHeight, setContentHeight] = useState<number | 'auto'>(isActive ? 'auto' : 0);

  // Measure content height when active state changes
  useEffect(() => {
    if (contentRef.current) {
      if (isActive) {
        setContentHeight(contentRef.current.scrollHeight);
        // After transition, switch to auto for responsive content
        const timer = setTimeout(() => setContentHeight('auto'), 300);
        return () => clearTimeout(timer);
      } else {
        // First set to actual height, then to 0 for animation
        setContentHeight(contentRef.current.scrollHeight);
        requestAnimationFrame(() => setContentHeight(0));
      }
    }
  }, [isActive]);

  // Check if header is clickable
  const isHeaderClickable = !disabled && (collapsible !== 'icon' || !showArrow);

  // Size-based padding
  const paddingMap = {
    small: { header: '8px 12px', content: '8px 12px' },
    medium: { header: '12px 16px', content: '16px' },
    large: { header: '16px 24px', content: '24px' },
  };
  const padding = paddingMap[size];

  // Panel container styles
  const panelStyle: CSSProperties = {
    borderBottom: bordered ? '1px solid #e0e0e0' : undefined,
    backgroundColor: '#fff',
    ...style,
  };

  // Header styles
  const headerStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    padding: padding.header,
    cursor: isHeaderClickable ? 'pointer' : 'default',
    userSelect: 'none',
    backgroundColor: isActive ? 'rgba(0, 0, 0, 0.02)' : 'transparent',
    transition: 'background-color 0.2s ease',
    opacity: disabled ? 0.5 : 1,
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    fontSize: '14px',
    fontWeight: 500,
    color: 'rgba(0, 0, 0, 0.87)',
  };

  // Expand icon styles
  const iconStyle: CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: 24,
    height: 24,
    marginRight: expandIconPosition === 'start' ? 8 : 0,
    marginLeft: expandIconPosition === 'end' ? 8 : 0,
    transition: 'transform 0.3s ease',
    transform: isActive ? 'rotate(90deg)' : 'rotate(0deg)',
    cursor: collapsible === 'icon' ? 'pointer' : undefined,
    fontSize: '12px',
    color: 'rgba(0, 0, 0, 0.45)',
    flexShrink: 0,
  };

  // Header text styles
  const headerTextStyle: CSSProperties = {
    flex: 1,
  };

  // Extra content styles
  const extraStyle: CSSProperties = {
    marginLeft: 8,
  };

  // Content wrapper styles (for animation)
  const contentWrapperStyle: CSSProperties = {
    height: contentHeight === 'auto' ? 'auto' : contentHeight,
    overflow: 'hidden',
    transition: contentHeight === 'auto' ? undefined : 'height 0.3s ease',
  };

  // Content inner styles
  const contentStyle: CSSProperties = {
    padding: padding.content,
  };

  // Handle header click
  const handleHeaderClick = () => {
    if (isHeaderClickable) {
      onToggle?.();
    }
  };

  // Handle icon click (for collapsible="icon" mode)
  const handleIconClick = (e: React.MouseEvent) => {
    if (collapsible === 'icon') {
      e.stopPropagation();
      onToggle?.();
    }
  };

  // Render expand icon
  const renderExpandIcon = () => {
    if (!showArrow) return null;

    const iconContent = typeof expandIcon === 'function' ? expandIcon(isActive) : expandIcon || '▶';

    return (
      <span style={iconStyle} onClick={handleIconClick}>
        {iconContent}
      </span>
    );
  };

  return (
    <div
      style={panelStyle}
      className={className}
      data-testid={dataTestId}
      data-expanded={isActive}
    >
      {/* Header */}
      <div
        style={headerStyle}
        onClick={handleHeaderClick}
        role="button"
        tabIndex={isHeaderClickable ? 0 : -1}
        aria-expanded={isActive}
        aria-disabled={disabled}
        onKeyDown={(e) => e.key === 'Enter' && isHeaderClickable && onToggle?.()}
        onMouseEnter={(e) => {
          if (isHeaderClickable) {
            e.currentTarget.style.backgroundColor = 'rgba(0, 0, 0, 0.04)';
          }
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.backgroundColor = isActive ? 'rgba(0, 0, 0, 0.02)' : 'transparent';
        }}
      >
        {/* Icon at start */}
        {expandIconPosition === 'start' && renderExpandIcon()}

        {/* Header text */}
        <span style={headerTextStyle}>{header}</span>

        {/* Extra content */}
        {extra && (
          <span style={extraStyle} onClick={(e) => e.stopPropagation()}>
            {extra}
          </span>
        )}

        {/* Icon at end */}
        {expandIconPosition === 'end' && renderExpandIcon()}
      </div>

      {/* Content (render if active or forceRender) */}
      {(isActive || forceRender || contentHeight !== 0) && (
        <div style={contentWrapperStyle}>
          <div ref={contentRef} style={contentStyle}>
            {children}
          </div>
        </div>
      )}
    </div>
  );
};

NativeCollapsePanel.displayName = 'NativeCollapsePanel';

// ============================================================================
// Collapse Component
// ============================================================================

/**
 * Native Collapse Component
 *
 * <p>Pure CSS implementation of CollapseProps from UIAdapter contract.
 * Container for collapsible content panels.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS with transitions</li>
 *   <li>Accordion mode (single panel) or multi-panel mode</li>
 *   <li>Customizable expand icon and position</li>
 *   <li>Controlled and uncontrolled modes</li>
 *   <li>Size variants: small, medium, large</li>
 *   <li>Bordered or borderless style</li>
 * </ul>
 *
 * <h3>Architectural Constraints:</h3>
 * <ul>
 *   <li>This component is an atomic building block</li>
 *   <li>Shell layer uses this via UIAdapter interface</li>
 *   <li>No direct import allowed in Plugin layer</li>
 * </ul>
 *
 * @example
 * ```tsx
 * const { Collapse, CollapsePanel } = useUI();
 *
 * // Using children
 * <Collapse accordion defaultActiveKey={['1']}>
 *   <CollapsePanel header="Section 1" key="1">
 *     Content 1
 *   </CollapsePanel>
 *   <CollapsePanel header="Section 2" key="2">
 *     Content 2
 *   </CollapsePanel>
 * </Collapse>
 *
 * // Using items array
 * <Collapse
 *   items={[
 *     { key: '1', label: 'Section 1', children: <Content1 /> },
 *     { key: '2', label: 'Section 2', children: <Content2 /> },
 *   ]}
 * />
 * ```
 *
 * @param props - CollapseProps from UIAdapter contract
 * @returns Native Collapse component
 */
export const NativeCollapse: FC<CollapseProps> = ({
  activeKey: controlledActiveKey,
  defaultActiveKey = [],
  accordion = false,
  bordered = true,
  expandIcon,
  expandIconPosition = 'start',
  collapsible,
  ghost = false,
  size = 'medium',
  destroyInactivePanel = false,
  onChange,
  items,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Normalize active keys to array
  const normalizeKeys = (keys: string | string[] | undefined): string[] => {
    if (!keys) return [];
    return Array.isArray(keys) ? keys : [keys];
  };

  // Internal state for uncontrolled mode
  const [internalActiveKeys, setInternalActiveKeys] = useState<string[]>(
    normalizeKeys(defaultActiveKey)
  );

  // Use controlled or internal keys
  const activeKeys = controlledActiveKey !== undefined 
    ? normalizeKeys(controlledActiveKey)
    : internalActiveKeys;

  // Handle panel toggle
  const handlePanelToggle = useCallback(
    (key: string) => {
      let newKeys: string[];

      if (accordion) {
        // Accordion mode: only one panel open
        newKeys = activeKeys.includes(key) ? [] : [key];
      } else {
        // Multi-panel mode: toggle the panel
        newKeys = activeKeys.includes(key)
          ? activeKeys.filter((k) => k !== key)
          : [...activeKeys, key];
      }

      if (controlledActiveKey === undefined) {
        setInternalActiveKeys(newKeys);
      }
      onChange?.(accordion ? newKeys[0] || '' : newKeys);
    },
    [accordion, activeKeys, controlledActiveKey, onChange]
  );

  // Container styles
  const containerStyle: CSSProperties = {
    borderRadius: bordered && !ghost ? 4 : undefined,
    border: bordered && !ghost ? '1px solid #e0e0e0' : undefined,
    backgroundColor: ghost ? 'transparent' : '#fff',
    overflow: 'hidden',
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    ...style,
  };

  // Build panels from items or children
  const renderPanels = () => {
    if (items && items.length > 0) {
      return items.map((item, index) => (
        <NativeCollapsePanel
          key={String(item.key)}
          header={item.label}
          extra={item.extra}
          disabled={item.collapsible === 'disabled'}
          showArrow={item.showArrow}
          forceRender={item.forceRender || !destroyInactivePanel}
          collapsible={item.collapsible || collapsible}
          isActive={activeKeys.includes(String(item.key))}
          onToggle={() => handlePanelToggle(String(item.key))}
          expandIconPosition={expandIconPosition}
          expandIcon={expandIcon}
          bordered={bordered && !ghost && index < items.length - 1}
          size={size}
        >
          {item.children}
        </NativeCollapsePanel>
      ));
    }

    // Render from children
    const childrenArray = Children.toArray(children);
    return childrenArray.map((child, index) => {
      if (!isValidElement(child)) return null;

      const panelProps = child.props as CollapsePanelProps;
      const key = String(child.key ?? index);

      return (
        <NativeCollapsePanel
          key={key}
          {...panelProps}
          isActive={activeKeys.includes(key)}
          onToggle={() => handlePanelToggle(key)}
          expandIconPosition={expandIconPosition}
          expandIcon={expandIcon}
          bordered={bordered && !ghost && index < childrenArray.length - 1}
          size={size}
          collapsible={panelProps.collapsible || collapsible}
          forceRender={panelProps.forceRender || !destroyInactivePanel}
        />
      );
    });
  };

  return (
    <div
      style={containerStyle}
      className={className}
      data-testid={dataTestId}
      data-accordion={accordion}
      data-ghost={ghost}
    >
      {renderPanels()}
    </div>
  );
};

NativeCollapse.displayName = 'NativeCollapse';

export default NativeCollapse;
