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
 * @file MUI Tabs Component Implementation
 * @description Material UI implementation of the Tabs navigation component
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiTabs
 * @version 1.0.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Implements UIAdapter contract for Tabs component
 * - Maps MUI Tabs API to unified BrixUI interface
 * - All tab components must be obtained via useUI() hook
 *
 * @example
 * ```tsx
 * import { useUI } from '@brix-sdk/runtime-sdk-api-web';
 *
 * function SettingsTabs() {
 *   const { Tabs } = useUI();
 *
 *   return (
 *     <Tabs
 *       items={[
 *         { key: 'general', label: 'General', children: <GeneralSettings /> },
 *         { key: 'security', label: 'Security', children: <SecuritySettings /> },
 *       ]}
 *     />
 *   );
 * }
 * ```
 */

import React, { type FC, useState, useCallback, useMemo } from 'react';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import CloseIcon from '@mui/icons-material/Close';
import AddIcon from '@mui/icons-material/Add';
import type {
  TabsProps,
  TabPaneProps,
  TabPosition,
} from '@brix-sdk/runtime-sdk-api-web';
import type { ComponentSize } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Size to Tab Height Mapping
 */
const SIZE_MAP: Record<ComponentSize, number> = {
  small: 36,
  medium: 48,
  large: 56,
};

/**
 * Tab Orientation Mapping
 */
const ORIENTATION_MAP: Record<TabPosition, 'horizontal' | 'vertical'> = {
  top: 'horizontal',
  bottom: 'horizontal',
  left: 'vertical',
  right: 'vertical',
};

/**
 * Tab Panel Content Component
 */
interface TabPanelProps {
  children?: React.ReactNode;
  value: string;
  activeKey: string;
  forceRender?: boolean;
}

const TabPanel: FC<TabPanelProps> = ({
  children,
  value,
  activeKey,
  forceRender = false,
}) => {
  const isActive = value === activeKey;

  // If not force render and not active, don't render content
  if (!forceRender && !isActive) {
    return null;
  }

  return (
    <Box
      role="tabpanel"
      hidden={!isActive}
      id={`tabpanel-${value}`}
      aria-labelledby={`tab-${value}`}
      sx={{ display: isActive ? 'block' : 'none' }}
    >
      {children}
    </Box>
  );
};

/**
 * MUI Tabs Component
 *
 * Material UI implementation of the UIAdapter Tabs interface.
 * Provides tabbed navigation with support for items array,
 * editable tabs, and vertical orientation.
 *
 * **Features:**
 * - Three tab types: line, card, editable-card
 * - Four positions: top, right, bottom, left
 * - Controlled and uncontrolled modes
 * - Tab item icons, disabled state
 * - Editable mode with add/remove
 *
 * @param props - TabsProps from UIAdapter contract
 * @returns React element
 */
export const MuiTabs: FC<TabsProps> = ({
  activeKey,
  defaultActiveKey,
  items = [],
  type = 'line',
  size = 'medium',
  tabPosition = 'top',
  centered = false,
  tabBarGutter,
  tabBarExtraContent,
  destroyInactiveTabPane = false,
  animated = true,
  onChange,
  onEdit,
  className,
  style,
  children,
}) => {
  // Handle controlled vs uncontrolled mode
  const [internalActiveKey, setInternalActiveKey] = useState<string>(
    defaultActiveKey || (items.length > 0 ? items[0].key : '')
  );

  const currentActiveKey = activeKey ?? internalActiveKey;

  /**
   * Handle tab change
   */
  const handleChange = useCallback(
    (_event: React.SyntheticEvent, newValue: string) => {
      if (!activeKey) {
        setInternalActiveKey(newValue);
      }
      onChange?.(newValue);
    },
    [activeKey, onChange]
  );

  /**
   * Handle tab close (for editable-card type)
   */
  const handleClose = useCallback(
    (key: string) => (event: React.MouseEvent) => {
      event.stopPropagation();
      onEdit?.(key, 'remove');
    },
    [onEdit]
  );

  /**
   * Handle add tab
   */
  const handleAdd = useCallback(() => {
    onEdit?.('', 'add');
  }, [onEdit]);

  /**
   * Get tab variant styling based on type
   */
  const getTabVariant = useMemo(() => {
    switch (type) {
      case 'card':
      case 'editable-card':
        return {
          '& .MuiTabs-indicator': {
            display: 'none',
          },
          '& .MuiTab-root': {
            border: '1px solid',
            borderColor: 'divider',
            borderBottom: 'none',
            borderRadius: '8px 8px 0 0',
            marginRight: '2px',
            minHeight: SIZE_MAP[size],
            '&.Mui-selected': {
              bgcolor: 'background.paper',
              borderBottomColor: 'background.paper',
            },
          },
        };
      case 'line':
      default:
        return {
          '& .MuiTab-root': {
            minHeight: SIZE_MAP[size],
          },
        };
    }
  }, [type, size]);

  /**
   * Build flex direction based on tab position
   */
  const getFlexDirection = (): 'column' | 'column-reverse' | 'row' | 'row-reverse' => {
    switch (tabPosition) {
      case 'bottom':
        return 'column-reverse';
      case 'left':
        return 'row';
      case 'right':
        return 'row-reverse';
      case 'top':
      default:
        return 'column';
    }
  };

  const orientation = ORIENTATION_MAP[tabPosition];
  const isVertical = orientation === 'vertical';

  // Determine if any items have panel content to render
  const hasPanelContent = items.some((item) => item.children != null);

  return (
    <Box
      className={className}
      style={style}
      sx={{
        display: 'flex',
        flexDirection: getFlexDirection(),
        // Only take full width when rendering tab panels; navigation-only mode uses natural width
        ...(hasPanelContent && { width: '100%' }),
        height: isVertical ? '100%' : 'auto',
      }}
    >
      {/* Tab Bar */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          borderBottom: !isVertical && tabPosition === 'top' ? 1 : 0,
          borderTop: !isVertical && tabPosition === 'bottom' ? 1 : 0,
          borderRight: isVertical && tabPosition === 'left' ? 1 : 0,
          borderLeft: isVertical && tabPosition === 'right' ? 1 : 0,
          borderColor: 'divider',
        }}
      >
        {/* Extra content at start */}
        {tabBarExtraContent &&
          typeof tabBarExtraContent === 'object' &&
          'left' in tabBarExtraContent && (
            <Box sx={{ px: 1 }}>{tabBarExtraContent.left}</Box>
          )}

        <Tabs
          value={currentActiveKey}
          onChange={handleChange}
          orientation={orientation}
          centered={centered && !isVertical}
          variant="scrollable"
          scrollButtons="auto"
          sx={{
            flex: 1,
            minHeight: SIZE_MAP[size],
            ...(tabBarGutter && {
              '& .MuiTab-root': {
                marginRight: `${tabBarGutter}px`,
              },
            }),
            ...getTabVariant,
          }}
        >
          {items.map((item) => (
            <Tab
              key={item.key}
              value={item.key}
              label={
                <Box
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 0.5,
                  }}
                >
                  {item.label}
                  {type === 'editable-card' && item.closable !== false && (
                    <IconButton
                      size="small"
                      onClick={handleClose(item.key)}
                      sx={{
                        p: 0.25,
                        ml: 0.5,
                        '&:hover': {
                          bgcolor: 'action.hover',
                        },
                      }}
                    >
                      <CloseIcon fontSize="small" sx={{ fontSize: 14 }} />
                    </IconButton>
                  )}
                </Box>
              }
              disabled={item.disabled}
              id={`tab-${item.key}`}
              aria-controls={`tabpanel-${item.key}`}
            />
          ))}
        </Tabs>

        {/* Add button for editable-card */}
        {type === 'editable-card' && (
          <IconButton
            size="small"
            onClick={handleAdd}
            sx={{ mx: 1 }}
            aria-label="Add tab"
          >
            <AddIcon />
          </IconButton>
        )}

        {/* Extra content at end */}
        {tabBarExtraContent && (
          <Box sx={{ px: 1 }}>
            {typeof tabBarExtraContent === 'object' && 'right' in tabBarExtraContent
              ? tabBarExtraContent.right
              : tabBarExtraContent}
          </Box>
        )}
      </Box>

      {/* Tab Panels — only rendered when items have content */}
      {hasPanelContent && (
      <Box
        sx={{
          flex: 1,
          p: 2,
          ...(animated && {
            transition: 'opacity 0.3s',
          }),
        }}
      >
        {items.map((item) => (
          <TabPanel
            key={item.key}
            value={item.key}
            activeKey={currentActiveKey}
            forceRender={item.forceRender || !destroyInactiveTabPane}
          >
            {item.children}
          </TabPanel>
        ))}
      </Box>
      )}
    </Box>
  );
};

/**
 * MUI TabPane Component
 *
 * Individual tab panel component for declarative Tabs usage.
 * Note: Using items prop on Tabs is recommended over TabPane children.
 *
 * @param props - TabPaneProps from UIAdapter contract
 * @returns React element
 */
export const MuiTabPane: FC<TabPaneProps> = ({
  tab,
  children,
  disabled = false,
  closable = true,
  forceRender = false,
  className,
  style,
}) => {
  // TabPane is primarily used as a data source for parent Tabs
  // The actual rendering is handled by MuiTabs
  return (
    <Box
      className={className}
      style={style}
      data-tab={tab}
      data-disabled={disabled}
      data-closable={closable}
      data-force-render={forceRender}
    >
      {children}
    </Box>
  );
};

export default MuiTabs;
