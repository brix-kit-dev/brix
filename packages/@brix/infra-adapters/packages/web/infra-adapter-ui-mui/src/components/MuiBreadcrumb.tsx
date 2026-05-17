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
 * @file MUI Breadcrumb Component Implementation
 * @description Material UI implementation of the Breadcrumb navigation component
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiBreadcrumb
 * @version 1.0.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Implements UIAdapter contract for Breadcrumb component
 * - Maps MUI Breadcrumbs API to unified BrixUI interface
 * - All breadcrumb components must be obtained via useUI() hook
 *
 * @example
 * ```tsx
 * import { useUI } from '@brix-sdk/runtime-sdk-api-web';
 *
 * function PageHeader() {
 *   const { Breadcrumb } = useUI();
 *
 *   return (
 *     <Breadcrumb
 *       items={[
 *         { label: 'Home', href: '/' },
 *         { label: 'Products', href: '/products' },
 *         { label: 'Current Product' },
 *       ]}
 *     />
 *   );
 * }
 * ```
 */

import React, { type FC, useCallback } from 'react';
import MuiBreadcrumbs from '@mui/material/Breadcrumbs';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Box from '@mui/material/Box';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
import HomeIcon from '@mui/icons-material/Home';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import type {
  BreadcrumbProps,
  BreadcrumbItem,
  BreadcrumbMenuItem,
} from '@brix-sdk/runtime-sdk-api-web';

/**
 * Icon Mapping for common breadcrumb icons
 */
const ICON_MAP: Record<string, React.ReactElement> = {
  home: <HomeIcon sx={{ fontSize: 18, mr: 0.5 }} />,
};

/**
 * Breadcrumb Item with Menu Component
 */
interface BreadcrumbWithMenuProps {
  item: BreadcrumbItem;
  isLast: boolean;
}

const BreadcrumbItemWithMenu: FC<BreadcrumbWithMenuProps> = ({ item, isLast }) => {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);

  const handleClick = useCallback((event: React.MouseEvent<HTMLElement>) => {
    event.preventDefault();
    setAnchorEl(event.currentTarget);
  }, []);

  const handleClose = useCallback(() => {
    setAnchorEl(null);
  }, []);

  const handleMenuItemClick = useCallback(
    (menuItem: BreadcrumbMenuItem) => () => {
      handleClose();
      if (menuItem.onClick) {
        menuItem.onClick();
      } else if (menuItem.href) {
        window.location.href = menuItem.href;
      }
    },
    [handleClose]
  );

  const content = (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        cursor: 'pointer',
      }}
      onClick={handleClick}
    >
      {item.icon && ICON_MAP[item.icon]}
      {item.label}
      <ExpandMoreIcon sx={{ fontSize: 16, ml: 0.25 }} />
    </Box>
  );

  return (
    <>
      {isLast ? (
        <Typography
          color="text.primary"
          sx={{ display: 'flex', alignItems: 'center' }}
        >
          {content}
        </Typography>
      ) : (
        <Link
          component="span"
          underline="hover"
          color="inherit"
          sx={{ display: 'flex', alignItems: 'center' }}
        >
          {content}
        </Link>
      )}
      <Menu
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'left',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'left',
        }}
      >
        {item.menu?.map((menuItem) => (
          <MenuItem
            key={menuItem.key}
            onClick={handleMenuItemClick(menuItem)}
          >
            {menuItem.icon && ICON_MAP[menuItem.icon]}
            {menuItem.label}
          </MenuItem>
        ))}
      </Menu>
    </>
  );
};

/**
 * MUI Breadcrumb Component
 *
 * Material UI implementation of the UIAdapter Breadcrumb interface.
 * Provides hierarchical navigation trail with optional dropdown menus.
 *
 * **Features:**
 * - Items array configuration
 * - Custom separator support
 * - Item icons
 * - Dropdown menus for sibling navigation
 * - Max items with collapse
 *
 * @param props - BreadcrumbProps from UIAdapter contract
 * @returns React element
 */
export const MuiBreadcrumb: FC<BreadcrumbProps> = ({
  items = [],
  separator,
  maxItems,
  itemsBeforeCollapse = 1,
  itemsAfterCollapse = 2,
  className,
  style,
}) => {
  /**
   * Handle item click
   */
  const handleItemClick = useCallback(
    (item: BreadcrumbItem) => (event: React.MouseEvent) => {
      if (item.onClick) {
        event.preventDefault();
        item.onClick();
      }
    },
    []
  );

  /**
   * Render a single breadcrumb item
   */
  const renderItem = (item: BreadcrumbItem, index: number) => {
    const isLast = index === items.length - 1;
    const key = item.key || `breadcrumb-${index}`;

    // If item has menu, render with dropdown
    if (item.menu && item.menu.length > 0) {
      return (
        <BreadcrumbItemWithMenu
          key={key}
          item={item}
          isLast={isLast}
        />
      );
    }

    // Last item is not a link
    if (isLast) {
      return (
        <Typography
          key={key}
          color="text.primary"
          sx={{ display: 'flex', alignItems: 'center' }}
        >
          {item.icon && ICON_MAP[item.icon]}
          {item.label}
        </Typography>
      );
    }

    // Item with href or onClick
    if (item.href || item.onClick) {
      return (
        <Link
          key={key}
          href={item.href || '#'}
          onClick={handleItemClick(item)}
          underline="hover"
          color="inherit"
          sx={{ display: 'flex', alignItems: 'center' }}
        >
          {item.icon && ICON_MAP[item.icon]}
          {item.label}
        </Link>
      );
    }

    // Plain text item
    return (
      <Typography
        key={key}
        color="text.secondary"
        sx={{ display: 'flex', alignItems: 'center' }}
      >
        {item.icon && ICON_MAP[item.icon]}
        {item.label}
      </Typography>
    );
  };

  /**
   * Get separator element
   */
  const getSeparator = () => {
    if (typeof separator === 'string') {
      return separator;
    }
    if (separator) {
      return separator;
    }
    return <NavigateNextIcon fontSize="small" />;
  };

  return (
    <MuiBreadcrumbs
      className={className}
      style={style}
      separator={getSeparator()}
      maxItems={maxItems}
      itemsBeforeCollapse={itemsBeforeCollapse}
      itemsAfterCollapse={itemsAfterCollapse}
      aria-label="breadcrumb"
    >
      {items.map(renderItem)}
    </MuiBreadcrumbs>
  );
};

export default MuiBreadcrumb;
