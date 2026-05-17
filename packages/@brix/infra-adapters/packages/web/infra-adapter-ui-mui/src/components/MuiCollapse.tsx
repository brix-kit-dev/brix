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
 * @file MUI Collapse Component Implementation
 * @description Material UI implementation of the Collapse/Accordion component
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiCollapse
 * @version 1.0.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Implements UIAdapter contract for Collapse component
 * - Maps MUI Accordion API to unified BrixUI Collapse interface
 * - All collapse components must be obtained via useUI() hook
 *
 * @example
 * ```tsx
 * import { useUI } from '@brix-sdk/runtime-sdk-api-web';
 *
 * function FAQSection() {
 *   const { Collapse } = useUI();
 *
 *   return (
 *     <Collapse
 *       accordion
 *       items={[
 *         { key: '1', label: 'What is Brix?', children: <Answer1 /> },
 *         { key: '2', label: 'How to get started?', children: <Answer2 /> },
 *       ]}
 *     />
 *   );
 * }
 * ```
 */

import React, { type FC, useState, useCallback, useMemo } from 'react';
import Accordion from '@mui/material/Accordion';
import AccordionSummary from '@mui/material/AccordionSummary';
import AccordionDetails from '@mui/material/AccordionDetails';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import type {
  CollapseProps,
  CollapsePanelProps,
  CollapseItem,
  ExpandIconPosition,
} from '@brix-sdk/runtime-sdk-api-web';
import type { ComponentSize } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Size to Padding Mapping
 */
const SIZE_PADDING_MAP: Record<ComponentSize, number> = {
  small: 8,
  medium: 12,
  large: 16,
};

/**
 * MUI Collapse Component
 *
 * Material UI implementation of the UIAdapter Collapse interface.
 * Uses MUI Accordion components to provide expandable content sections.
 *
 * **Features:**
 * - Items array or CollapsePanel children
 * - Accordion mode (single panel open)
 * - Controlled and uncontrolled modes
 * - Ghost (borderless) style
 * - Configurable expand icon position
 *
 * @param props - CollapseProps from UIAdapter contract
 * @returns React element
 */
export const MuiCollapse: FC<CollapseProps> = ({
  activeKey,
  defaultActiveKey,
  items = [],
  accordion = false,
  bordered = true,
  ghost = false,
  expandIconPosition = 'start',
  size = 'medium',
  onChange,
  className,
  style,
  children,
}) => {
  /**
   * Normalize active keys to array
   */
  const normalizeKeys = (keys: string | string[] | undefined): string[] => {
    if (!keys) return [];
    if (Array.isArray(keys)) return keys;
    return [keys];
  };

  // Handle controlled vs uncontrolled mode
  const [internalActiveKeys, setInternalActiveKeys] = useState<string[]>(
    normalizeKeys(defaultActiveKey)
  );

  const currentActiveKeys = activeKey !== undefined
    ? normalizeKeys(activeKey)
    : internalActiveKeys;

  /**
   * Handle panel expansion change
   */
  const handleChange = useCallback(
    (panelKey: string) => (_event: React.SyntheticEvent, isExpanded: boolean) => {
      let newKeys: string[];

      if (accordion) {
        // Accordion mode: only one panel open at a time
        newKeys = isExpanded ? [panelKey] : [];
      } else {
        // Multi-mode: toggle the panel
        if (isExpanded) {
          newKeys = [...currentActiveKeys, panelKey];
        } else {
          newKeys = currentActiveKeys.filter((k) => k !== panelKey);
        }
      }

      if (activeKey === undefined) {
        setInternalActiveKeys(newKeys);
      }

      onChange?.(accordion ? (newKeys[0] || '') : newKeys);
    },
    [accordion, activeKey, currentActiveKeys, onChange]
  );

  /**
   * Common accordion styling
   */
  const accordionSx = useMemo(
    () => ({
      ...(ghost && {
        bgcolor: 'transparent',
        boxShadow: 'none',
        '&:before': {
          display: 'none',
        },
      }),
      ...(!bordered && {
        border: 'none',
        '&:before': {
          display: 'none',
        },
      }),
      '&.Mui-expanded': {
        margin: 0,
      },
    }),
    [ghost, bordered]
  );

  /**
   * Render a single collapse panel
   */
  const renderPanel = (item: CollapseItem) => {
    const isExpanded = currentActiveKeys.includes(item.key);
    const isDisabled = item.disabled || item.collapsible === 'disabled';
    const showArrow = item.showArrow !== false;

    return (
      <Accordion
        key={item.key}
        expanded={isExpanded}
        onChange={handleChange(item.key)}
        disabled={isDisabled}
        disableGutters
        sx={accordionSx}
        TransitionProps={{
          unmountOnExit: !item.forceRender,
        }}
      >
        <AccordionSummary
          expandIcon={
            showArrow ? (
              <ExpandMoreIcon />
            ) : undefined
          }
          sx={{
            flexDirection:
              expandIconPosition === 'end' ? 'row' : 'row-reverse',
            '& .MuiAccordionSummary-expandIconWrapper': {
              marginLeft: expandIconPosition === 'end' ? 'auto' : 0,
              marginRight: expandIconPosition === 'end' ? 0 : 1,
            },
            '& .MuiAccordionSummary-content': {
              marginLeft: expandIconPosition === 'start' ? 1 : 0,
            },
            padding: `0 ${SIZE_PADDING_MAP[size]}px`,
            minHeight: 48,
            ...(item.collapsible === 'icon' && {
              cursor: 'default',
              '&:hover': {
                cursor: 'default',
              },
            }),
          }}
          onClick={
            item.collapsible === 'icon'
              ? (e) => e.stopPropagation()
              : undefined
          }
        >
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              width: '100%',
            }}
          >
            <Typography sx={{ fontWeight: 500 }}>{item.label}</Typography>
            {item.extra && <Box sx={{ ml: 2 }}>{item.extra}</Box>}
          </Box>
        </AccordionSummary>
        <AccordionDetails
          sx={{
            padding: SIZE_PADDING_MAP[size],
          }}
        >
          {item.children}
        </AccordionDetails>
      </Accordion>
    );
  };

  return (
    <Box
      className={className}
      style={style}
      sx={{
        ...(bordered && !ghost && {
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 1,
          overflow: 'hidden',
        }),
      }}
    >
      {items.map(renderPanel)}
    </Box>
  );
};

/**
 * MUI CollapsePanel Component
 *
 * Individual collapse panel component for declarative Collapse usage.
 * Note: Using items prop on Collapse is recommended over CollapsePanel children.
 *
 * @param props - CollapsePanelProps from UIAdapter contract
 * @returns React element
 */
export const MuiCollapsePanel: FC<CollapsePanelProps> = ({
  header,
  children,
  disabled = false,
  showArrow = true,
  extra,
  forceRender = false,
  collapsible = 'header',
  className,
  style,
}) => {
  // CollapsePanel is primarily used as a data source for parent Collapse
  // The actual rendering is handled by MuiCollapse
  return (
    <Box
      className={className}
      style={style}
      data-header={header}
      data-disabled={disabled}
      data-show-arrow={showArrow}
      data-force-render={forceRender}
      data-collapsible={collapsible}
    >
      {extra && <Box data-extra>{extra}</Box>}
      {children}
    </Box>
  );
};

export default MuiCollapse;
