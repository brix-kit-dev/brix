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
 * @file Native Tabs Component
 * @description Pure CSS implementation of TabsProps and TabPaneProps from UIAdapter contract.
 *              Tab navigation with content panels.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeTabs
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies  
 * - Three tab types: line, card, editable-card
 * - Four positions: top, bottom, left, right
 * - Keyboard navigation support
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic navigation component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for tabbed interfaces.
 * Replaces direct MUI Tabs usage in enterprise-solutions plugins.
 */

import type { FC, CSSProperties, ReactNode, ReactElement, Children as ReactChildren } from 'react';
import { useState, useCallback, isValidElement, Children } from 'react';
import type { TabsProps, TabPaneProps, TabsType, TabPosition } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Size Mappings
// ============================================================================

/**
 * Tab Size Mappings
 *
 * <p>Padding and font sizes for tabs.</p>
 */
const SIZE_MAP = {
  small: { padding: '8px 12px', fontSize: '13px' },
  medium: { padding: '12px 16px', fontSize: '14px' },
  large: { padding: '16px 24px', fontSize: '15px' },
};

// ============================================================================
// TabPane Component
// ============================================================================

/**
 * Native TabPane Component
 *
 * <p>Individual tab panel containing content for one tab.
 * Used as a child of NativeTabs.</p>
 *
 * @example
 * ```tsx
 * <TabPane tab="Tab 1" key="1">
 *   Content for tab 1
 * </TabPane>
 * ```
 *
 * @param props - TabPaneProps from UIAdapter contract
 * @returns TabPane content (rendered by parent Tabs)
 */
export const NativeTabPane: FC<TabPaneProps> = ({
  children,
}) => {
  // Rendering is handled by parent Tabs component
  return <>{children}</>;
};

NativeTabPane.displayName = 'NativeTabPane';

// ============================================================================
// Tabs Component
// ============================================================================

/**
 * Native Tabs Component
 *
 * <p>Pure CSS implementation of TabsProps from UIAdapter contract.
 * Tab-based navigation with content panels.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS</li>
 *   <li>Three tab types: line, card, editable-card</li>
 *   <li>Four positions: top, bottom, left, right</li>
 *   <li>Closable tabs for editable-card type</li>
 *   <li>Keyboard navigation (arrow keys)</li>
 *   <li>Controlled and uncontrolled modes</li>
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
 * const { Tabs, TabPane } = useUI();
 *
 * <Tabs activeKey={activeTab} onChange={setActiveTab}>
 *   <TabPane tab="Users" key="users">
 *     <UserList />
 *   </TabPane>
 *   <TabPane tab="Settings" key="settings">
 *     <SettingsForm />
 *   </TabPane>
 * </Tabs>
 * ```
 *
 * @param props - TabsProps from UIAdapter contract
 * @returns Native Tabs component
 */
export const NativeTabs: FC<TabsProps> = ({
  activeKey: controlledActiveKey,
  defaultActiveKey,
  type = 'line',
  tabPosition = 'top',
  size = 'medium',
  centered = false,
  tabBarGutter,
  tabBarExtraContent,
  animated = true,
  destroyInactiveTabPane = false,
  onChange,
  onEdit,
  onTabClick,
  items,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Extract tab items from children or items prop
  const tabItems = items || extractTabsFromChildren(children);

  // Determine default key
  const firstKey = tabItems[0]?.key || '0';
  const defaultKey = defaultActiveKey || firstKey;

  // Internal state for uncontrolled mode
  const [internalActiveKey, setInternalActiveKey] = useState(defaultKey);

  // Use controlled or uncontrolled key
  const activeKey = controlledActiveKey ?? internalActiveKey;

  // Handle tab change
  const handleTabChange = useCallback(
    (key: string) => {
      if (controlledActiveKey === undefined) {
        setInternalActiveKey(key);
      }
      onChange?.(key);
    },
    [controlledActiveKey, onChange]
  );

  // Handle tab click
  const handleTabClick = useCallback(
    (key: string, e: React.MouseEvent | React.KeyboardEvent) => {
      onTabClick?.(key, e);
      handleTabChange(key);
    },
    [onTabClick, handleTabChange]
  );

  // Handle tab close
  const handleTabClose = useCallback(
    (key: string, e: React.MouseEvent) => {
      e.stopPropagation();
      onEdit?.(key, 'remove');
    },
    [onEdit]
  );

  // Handle add new tab
  const handleAddTab = useCallback(() => {
    onEdit?.('', 'add');
  }, [onEdit]);

  // Keyboard navigation
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      const currentIndex = tabItems.findIndex((item) => item.key === activeKey);
      let newIndex = currentIndex;

      if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
        newIndex = (currentIndex - 1 + tabItems.length) % tabItems.length;
      } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
        newIndex = (currentIndex + 1) % tabItems.length;
      }

      if (newIndex !== currentIndex) {
        const newItem = tabItems[newIndex];
        if (newItem && !newItem.disabled) {
          handleTabChange(String(newItem.key));
        }
      }
    },
    [tabItems, activeKey, handleTabChange]
  );

  // Is vertical layout?
  const isVertical = tabPosition === 'left' || tabPosition === 'right';

  // Get size styles
  const sizeStyles = SIZE_MAP[size] || SIZE_MAP.medium;

  // Container styles
  const containerStyle: CSSProperties = {
    display: 'flex',
    flexDirection: isVertical
      ? tabPosition === 'left'
        ? 'row'
        : 'row-reverse'
      : tabPosition === 'top'
      ? 'column'
      : 'column-reverse',
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    ...style,
  };

  // Tab bar styles
  const tabBarStyle: CSSProperties = {
    display: 'flex',
    flexDirection: isVertical ? 'column' : 'row',
    alignItems: centered && !isVertical ? 'center' : 'stretch',
    justifyContent: centered && !isVertical ? 'center' : 'flex-start',
    gap: tabBarGutter || (type === 'card' ? 2 : 0),
    borderBottom: !isVertical && tabPosition === 'top' && type === 'line' ? '1px solid #e0e0e0' : undefined,
    borderTop: !isVertical && tabPosition === 'bottom' && type === 'line' ? '1px solid #e0e0e0' : undefined,
    borderRight: isVertical && tabPosition === 'left' && type === 'line' ? '1px solid #e0e0e0' : undefined,
    borderLeft: isVertical && tabPosition === 'right' && type === 'line' ? '1px solid #e0e0e0' : undefined,
    flexShrink: 0,
    position: 'relative',
  };

  // Tab bar wrapper (for extra content)
  const tabBarWrapperStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    width: !isVertical ? '100%' : undefined,
  };

  // Tab button styles
  const getTabStyle = (item: typeof tabItems[0]): CSSProperties => {
    const isActive = String(item.key) === activeKey;
    const isDisabled = item.disabled;

    const baseStyle: CSSProperties = {
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      padding: sizeStyles.padding,
      fontSize: sizeStyles.fontSize,
      fontWeight: isActive ? 500 : 400,
      color: isDisabled ? 'rgba(0, 0, 0, 0.38)' : isActive ? '#1976d2' : 'rgba(0, 0, 0, 0.87)',
      backgroundColor: 'transparent',
      border: 'none',
      cursor: isDisabled ? 'not-allowed' : 'pointer',
      position: 'relative',
      transition: 'color 0.2s ease, background-color 0.2s ease',
      whiteSpace: 'nowrap',
    };

    // Line type
    if (type === 'line') {
      return {
        ...baseStyle,
        borderBottom:
          !isVertical && tabPosition === 'top'
            ? isActive
              ? '2px solid #1976d2'
              : '2px solid transparent'
            : undefined,
        borderTop:
          !isVertical && tabPosition === 'bottom'
            ? isActive
              ? '2px solid #1976d2'
              : '2px solid transparent'
            : undefined,
        borderRight:
          isVertical && tabPosition === 'left'
            ? isActive
              ? '2px solid #1976d2'
              : '2px solid transparent'
            : undefined,
        borderLeft:
          isVertical && tabPosition === 'right'
            ? isActive
              ? '2px solid #1976d2'
              : '2px solid transparent'
            : undefined,
        marginBottom: !isVertical && tabPosition === 'top' ? -1 : undefined,
        marginTop: !isVertical && tabPosition === 'bottom' ? -1 : undefined,
        marginRight: isVertical && tabPosition === 'left' ? -1 : undefined,
        marginLeft: isVertical && tabPosition === 'right' ? -1 : undefined,
      };
    }

    // Card type
    if (type === 'card' || type === 'editable-card') {
      return {
        ...baseStyle,
        backgroundColor: isActive ? '#fff' : '#fafafa',
        border: '1px solid #e0e0e0',
        borderBottom: !isVertical && tabPosition === 'top' && isActive ? '1px solid #fff' : '1px solid #e0e0e0',
        borderTop: !isVertical && tabPosition === 'bottom' && isActive ? '1px solid #fff' : '1px solid #e0e0e0',
        borderRadius:
          tabPosition === 'top'
            ? '4px 4px 0 0'
            : tabPosition === 'bottom'
            ? '0 0 4px 4px'
            : tabPosition === 'left'
            ? '4px 0 0 4px'
            : '0 4px 4px 0',
        marginBottom: !isVertical && tabPosition === 'top' && isActive ? -1 : undefined,
        marginTop: !isVertical && tabPosition === 'bottom' && isActive ? -1 : undefined,
      };
    }

    return baseStyle;
  };

  // Close button styles
  const closeButtonStyle: CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: 16,
    height: 16,
    marginLeft: 4,
    borderRadius: '50%',
    border: 'none',
    backgroundColor: 'transparent',
    color: 'rgba(0, 0, 0, 0.45)',
    cursor: 'pointer',
    fontSize: '12px',
    transition: 'background-color 0.2s, color 0.2s',
  };

  // Add tab button styles
  const addButtonStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: sizeStyles.padding,
    fontSize: '16px',
    color: 'rgba(0, 0, 0, 0.45)',
    backgroundColor: 'transparent',
    border: '1px dashed #e0e0e0',
    borderRadius: tabPosition === 'top' ? '4px 4px 0 0' : '0 0 4px 4px',
    cursor: 'pointer',
    transition: 'border-color 0.2s, color 0.2s',
  };

  // Content panel styles
  const contentStyle: CSSProperties = {
    flex: 1,
    padding: 16,
    overflow: 'auto',
  };

  // Find active tab content
  const activeItem = tabItems.find((item) => String(item.key) === activeKey);

  return (
    <div
      style={containerStyle}
      className={className}
      data-testid={dataTestId}
      data-type={type}
      data-position={tabPosition}
    >
      {/* Tab bar */}
      <div style={tabBarWrapperStyle}>
        <div style={tabBarStyle} role="tablist" onKeyDown={handleKeyDown}>
          {tabItems.map((item) => (
            <button
              key={String(item.key)}
              type="button"
              role="tab"
              aria-selected={String(item.key) === activeKey}
              aria-disabled={item.disabled}
              tabIndex={String(item.key) === activeKey ? 0 : -1}
              style={getTabStyle(item)}
              onClick={(e) => !item.disabled && handleTabClick(String(item.key), e)}
              onMouseEnter={(e) => {
                if (!item.disabled) {
                  e.currentTarget.style.backgroundColor = 'rgba(0, 0, 0, 0.04)';
                }
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor =
                  type === 'card' || type === 'editable-card'
                    ? String(item.key) === activeKey
                      ? '#fff'
                      : '#fafafa'
                    : 'transparent';
              }}
            >
              {/* Icon */}
              {item.icon && <span>{item.icon}</span>}

              {/* Label */}
              <span>{item.tab || item.label}</span>

              {/* Close button for editable-card */}
              {type === 'editable-card' && item.closable !== false && (
                <span
                  role="button"
                  tabIndex={-1}
                  style={closeButtonStyle}
                  onClick={(e) => handleTabClose(String(item.key), e)}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.backgroundColor = 'rgba(0, 0, 0, 0.08)';
                    e.currentTarget.style.color = 'rgba(0, 0, 0, 0.87)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.backgroundColor = 'transparent';
                    e.currentTarget.style.color = 'rgba(0, 0, 0, 0.45)';
                  }}
                >
                  ✕
                </span>
              )}
            </button>
          ))}

          {/* Add button for editable-card */}
          {type === 'editable-card' && (
            <button
              type="button"
              style={addButtonStyle}
              onClick={handleAddTab}
              onMouseEnter={(e) => {
                e.currentTarget.style.borderColor = '#1976d2';
                e.currentTarget.style.color = '#1976d2';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.borderColor = '#e0e0e0';
                e.currentTarget.style.color = 'rgba(0, 0, 0, 0.45)';
              }}
            >
              +
            </button>
          )}
        </div>

        {/* Extra content */}
        {tabBarExtraContent && <div style={{ flexShrink: 0 }}>{tabBarExtraContent}</div>}
      </div>

      {/* Tab content */}
      <div style={contentStyle} role="tabpanel">
        {destroyInactiveTabPane
          ? activeItem?.children
          : tabItems.map((item) => (
              <div
                key={String(item.key)}
                style={{
                  display: String(item.key) === activeKey ? 'block' : 'none',
                  transition: animated ? 'opacity 0.2s ease' : undefined,
                }}
              >
                {item.children}
              </div>
            ))}
      </div>
    </div>
  );
};

NativeTabs.displayName = 'NativeTabs';

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Extract tab items from TabPane children
 *
 * @param children - React children containing TabPane elements
 * @returns Array of tab item configurations
 */
function extractTabsFromChildren(
  children: ReactNode
): Array<{ key: string; tab?: ReactNode; label?: ReactNode; icon?: ReactNode; disabled?: boolean; closable?: boolean; children?: ReactNode }> {
  const items: Array<{
    key: string;
    tab?: ReactNode;
    label?: ReactNode;
    icon?: ReactNode;
    disabled?: boolean;
    closable?: boolean;
    children?: ReactNode;
  }> = [];

  Children.forEach(children, (child, index) => {
    if (isValidElement(child)) {
      const props = child.props as TabPaneProps & { children?: ReactNode };
      items.push({
        key: String(child.key ?? index),
        tab: props.tab,
        label: props.tab,
        icon: props.icon,
        disabled: props.disabled,
        closable: props.closable,
        children: props.children,
      });
    }
  });

  return items;
}

export default NativeTabs;
