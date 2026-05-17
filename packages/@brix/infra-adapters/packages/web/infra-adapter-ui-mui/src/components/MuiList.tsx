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
 * @file MUI List Component
 * @description Material UI implementation of ListProps from UIAdapter contract.
 *              Structured vertical list container for repeated content.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiList
 * @version 3.2.0
 *
 * [Design Principles]
 * - Direct mapping from ListProps to MUI List API
 * - Supports dividers, dense mode, and subheaders
 * - ListItem supports avatar, primary/secondary text, actions
 * - Consistent spacing and styling
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic data display component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for list-based layouts.
 * Replaces direct MUI List usage in enterprise-solutions plugins.
 */

import type { FC } from 'react';
import type { ListProps, ListItemProps } from '@brix-sdk/runtime-sdk-api-web';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemAvatar from '@mui/material/ListItemAvatar';
import ListItemText from '@mui/material/ListItemText';
import ListItemSecondaryAction from '@mui/material/ListItemSecondaryAction';
import ListItemButton from '@mui/material/ListItemButton';
import ListSubheader from '@mui/material/ListSubheader';

// ============================================================================
// MuiList Component Implementation
// ============================================================================

/**
 * MUI List Component
 *
 * <p>Material UI implementation of ListProps from UIAdapter contract.
 * Provides a container for displaying vertical lists of items with
 * consistent styling and spacing.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Built on MUI List for consistent styling</li>
 *   <li>Dense mode for compact display</li>
 *   <li>Optional dividers between items</li>
 *   <li>Subheader support</li>
 *   <li>Disable padding option</li>
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
 * const { List, ListItem, Avatar } = useUI();
 *
 * <List divider size="small">
 *   {users.map(user => (
 *     <ListItem
 *       key={user.id}
 *       avatar={<Avatar src={user.avatar} />}
 *       primary={user.name}
 *       secondary={user.email}
 *     />
 *   ))}
 * </List>
 * ```
 *
 * @param props - ListProps from UIAdapter contract
 * @returns MUI List component
 */
export const MuiList: FC<ListProps> = ({
  size = 'medium',
  divider: _divider = false, // Reserved for future use with ListItem context
  disablePadding = false,
  subheader,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Determine if dense mode based on size
  const dense = size === 'small';

  // Build subheader element if provided
  const subheaderElement = subheader ? (
    <ListSubheader component="div">{subheader}</ListSubheader>
  ) : undefined;

  return (
    <List
      dense={dense}
      disablePadding={disablePadding}
      subheader={subheaderElement}
      sx={style}
      className={className}
      data-testid={dataTestId}
    >
      {children}
    </List>
  );
};

// ============================================================================
// MuiListItem Component Implementation
// ============================================================================

/**
 * MUI ListItem Component
 *
 * <p>Material UI implementation of ListItemProps from UIAdapter contract.
 * Individual item within a List supporting avatars, text, and actions.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Built on MUI ListItem and ListItemButton</li>
 *   <li>Avatar support at start</li>
 *   <li>Primary and secondary text</li>
 *   <li>Secondary action slot at end</li>
 *   <li>Selected and disabled states</li>
 *   <li>Optional divider below</li>
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
 * const { ListItem, Avatar, Button } = useUI();
 *
 * <ListItem
 *   avatar={<Avatar src={user.photo} />}
 *   primary={user.name}
 *   secondary={user.role}
 *   secondaryAction={
 *     <Button size="small" variant="text" onClick={() => edit(user)}>
 *       Edit
 *     </Button>
 *   }
 *   onClick={() => select(user)}
 * />
 * ```
 *
 * @param props - ListItemProps from UIAdapter contract
 * @returns MUI ListItem/ListItemButton component
 */
export const MuiListItem: FC<ListItemProps> = ({
  avatar,
  primary,
  secondary,
  secondaryAction,
  selected = false,
  disabled = false,
  divider = false,
  onClick,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Determine if item is interactive (clickable)
  const isInteractive = Boolean(onClick);

  // Build avatar element
  const avatarElement = avatar ? (
    <ListItemAvatar>{avatar}</ListItemAvatar>
  ) : null;

  // Build text element - use children if primary/secondary not provided
  const textElement =
    primary || secondary ? (
      <ListItemText primary={primary} secondary={secondary} />
    ) : children ? (
      <ListItemText primary={children} />
    ) : null;

  // Build secondary action element
  const actionElement = secondaryAction ? (
    <ListItemSecondaryAction>{secondaryAction}</ListItemSecondaryAction>
  ) : null;

  // Render interactive ListItemButton or static ListItem
  if (isInteractive) {
    return (
      <ListItem
        disablePadding
        secondaryAction={actionElement}
        divider={divider}
        sx={style}
        className={className}
        data-testid={dataTestId}
      >
        <ListItemButton
          selected={selected}
          disabled={disabled}
          onClick={onClick}
        >
          {avatarElement}
          {textElement}
        </ListItemButton>
      </ListItem>
    );
  }

  return (
    <ListItem
      selected={selected}
      disabled={disabled}
      divider={divider}
      secondaryAction={actionElement}
      sx={style}
      className={className}
      data-testid={dataTestId}
    >
      {avatarElement}
      {textElement}
    </ListItem>
  );
};

export default MuiList;
