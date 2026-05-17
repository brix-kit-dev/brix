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
 * @file MUI Tag Component
 * @description Material UI implementation of TagProps from UIAdapter contract.
 *              Wraps MUI Chip component for labeling and categorization.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiTag
 * @version 3.2.0
 *
 * [Design Principles]
 * - Named 'Tag' for Ant Design compatibility (wraps MUI Chip)
 * - Supports closable, clickable, and various color modes
 * - Icon support via MuiIcon component
 * - Consistent with UIAdapter minimal common interface
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic data display component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for labeling and filtering.
 * Replaces direct MUI Chip usage in enterprise-solutions plugins.
 */

import type { FC } from 'react';
import type { TagProps, TagColor, TagVariant, ComponentSize } from '@brix-sdk/runtime-sdk-api-web';
import Chip from '@mui/material/Chip';
import { MuiIcon } from '../icons/MuiIcon';

// ============================================================================
// Color & Size Mappings
// ============================================================================

/**
 * Maps UIAdapter TagColor to MUI Chip color
 *
 * <p>Maps semantic color names to MUI color prop values.</p>
 */
const COLOR_MAP: Record<TagColor, 'default' | 'primary' | 'success' | 'warning' | 'error' | 'info'> = {
  default: 'default',
  primary: 'primary',
  success: 'success',
  warning: 'warning',
  error: 'error',
  info: 'info',
};

/**
 * Maps UIAdapter TagVariant to MUI Chip variant
 */
const VARIANT_MAP: Record<TagVariant, 'filled' | 'outlined'> = {
  filled: 'filled',
  outlined: 'outlined',
};

/**
 * Maps UIAdapter ComponentSize to MUI Chip size
 */
const SIZE_MAP: Record<ComponentSize, 'small' | 'medium'> = {
  small: 'small',
  medium: 'medium',
  large: 'medium',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Tag Component
 *
 * <p>Material UI implementation of TagProps from UIAdapter contract.
 * Wraps MUI Chip to provide labeling and categorization functionality.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Built on MUI Chip for consistent styling</li>
 *   <li>6 semantic color options</li>
 *   <li>Filled and outlined variants</li>
 *   <li>Closable mode with close button</li>
 *   <li>Clickable mode for interactive tags</li>
 *   <li>Optional icon support</li>
 * </ul>
 *
 * <h3>Architectural Constraints:</h3>
 * <ul>
 *   <li>This component is an atomic building block</li>
 *   <li>Shell layer uses this via UIAdapter interface</li>
 *   <li>No direct import allowed in Plugin layer</li>
 *   <li>Named 'Tag' for cross-library compatibility</li>
 * </ul>
 *
 * @example
 * ```tsx
 * const { Tag, Stack } = useUI();
 *
 * // Status tags
 * <Stack direction="row" spacing={8}>
 *   <Tag color="success">Active</Tag>
 *   <Tag color="warning">Pending</Tag>
 *   <Tag color="error">Expired</Tag>
 * </Stack>
 *
 * // Closable tag
 * <Tag closable onClose={() => handleRemove(tag.id)}>
 *   {tag.name}
 * </Tag>
 *
 * // Clickable filter tag
 * <Tag
 *   clickable
 *   variant={isSelected ? 'filled' : 'outlined'}
 *   onClick={() => toggleFilter(category)}
 * >
 *   {category.label}
 * </Tag>
 * ```
 *
 * @param props - TagProps from UIAdapter contract
 * @returns MUI Chip component styled as Tag
 */
export const MuiTag: FC<TagProps> = ({
  color = 'default',
  variant = 'filled',
  size = 'medium',
  closable = false,
  clickable = false,
  icon,
  disabled = false,
  onClose,
  onClick,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Build icon element if provided
  const iconElement = icon ? <MuiIcon name={icon} size="small" /> : undefined;

  // Handle close event - only if closable
  const handleDelete = closable && onClose ? onClose : undefined;

  // Handle click event - only if clickable and not disabled
  const handleClick =
    clickable && onClick && !disabled
      ? (event: React.MouseEvent) => {
          event.stopPropagation();
          onClick();
        }
      : undefined;

  return (
    <Chip
      label={children}
      color={COLOR_MAP[color]}
      variant={VARIANT_MAP[variant]}
      size={SIZE_MAP[size]}
      disabled={disabled}
      icon={iconElement}
      onDelete={handleDelete}
      onClick={handleClick}
      clickable={clickable && !disabled}
      sx={style}
      className={className}
      data-testid={dataTestId}
    />
  );
};

export default MuiTag;
