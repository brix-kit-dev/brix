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
 * @file Native Paper Component
 * @description Pure CSS implementation of PaperProps from UIAdapter contract.
 *              Elevated surface component for content grouping with shadows.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativePaper
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Shadow elevation system matching Material Design spec
 * - Supports outlined variant as alternative to shadows
 * - Component polymorphism for semantic HTML
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic layout component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for elevated surfaces.
 * Replaces direct MUI Paper usage in enterprise-solutions plugins.
 */

import { createElement, type FC, type CSSProperties } from 'react';
import type { PaperProps, PaperElevation } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Shadow Elevation System
// ============================================================================

/**
 * Material Design Elevation Shadows
 *
 * <p>Box shadow values matching Material Design elevation spec.
 * Each level provides progressively more prominent shadows.</p>
 */
const ELEVATION_SHADOWS: Record<PaperElevation, string> = {
  0: 'none',
  1: '0 2px 1px -1px rgba(0,0,0,0.2), 0 1px 1px 0 rgba(0,0,0,0.14), 0 1px 3px 0 rgba(0,0,0,0.12)',
  2: '0 3px 1px -2px rgba(0,0,0,0.2), 0 2px 2px 0 rgba(0,0,0,0.14), 0 1px 5px 0 rgba(0,0,0,0.12)',
  3: '0 3px 3px -2px rgba(0,0,0,0.2), 0 3px 4px 0 rgba(0,0,0,0.14), 0 1px 8px 0 rgba(0,0,0,0.12)',
  4: '0 2px 4px -1px rgba(0,0,0,0.2), 0 4px 5px 0 rgba(0,0,0,0.14), 0 1px 10px 0 rgba(0,0,0,0.12)',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Paper Component
 *
 * <p>Pure CSS implementation of PaperProps from UIAdapter contract.
 * Provides an elevated surface for grouping related content with
 * visual depth through shadows or borders.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS shadows</li>
 *   <li>5-level elevation system (0-4)</li>
 *   <li>Outlined variant for bordered surfaces</li>
 *   <li>Square corners option</li>
 *   <li>Component polymorphism for semantic HTML</li>
 * </ul>
 *
 * <h3>Architectural Constraints:</h3>
 * <ul>
 *   <li>This component is an atomic building block</li>
 *   <li>Layout components use this via UIAdapter interface</li>
 *   <li>No direct import allowed in Plugin layer</li>
 * </ul>
 *
 * @example
 * ```tsx
 * // Basic elevated surface
 * const { Paper, Typography } = useUI();
 *
 * <Paper elevation={1} style={{ padding: 16 }}>
 *   <Typography>Card content here</Typography>
 * </Paper>
 *
 * // Outlined variant (no shadow)
 * <Paper variant="outlined" style={{ padding: 24 }}>
 *   <Typography>Bordered content</Typography>
 * </Paper>
 *
 * // Higher elevation for dialogs
 * <Paper elevation={3} style={{ padding: 32, borderRadius: 8 }}>
 *   <Typography variant="h6">Dialog Title</Typography>
 * </Paper>
 * ```
 *
 * @param props - PaperProps from UIAdapter contract
 * @returns Native Paper component
 */
export const NativePaper: FC<PaperProps> = ({
  elevation = 1,
  variant = 'elevation',
  square = false,
  component = 'div',
  style,
  className,
  id,
  onClick,
  'data-testid': dataTestId,
  children,
}) => {
  // Determine shadow based on variant
  const isOutlined = variant === 'outlined';
  const boxShadow = isOutlined ? 'none' : ELEVATION_SHADOWS[elevation];

  // Build paper styles
  const paperStyle: CSSProperties = {
    backgroundColor: '#ffffff',
    borderRadius: square ? 0 : '4px',
    boxShadow,
    border: isOutlined ? '1px solid rgba(0, 0, 0, 0.12)' : 'none',
    overflow: 'hidden',
    ...style,
  };

  // Build props for createElement
  const props: Record<string, unknown> = {
    style: paperStyle,
    className,
    id,
    onClick,
    'data-testid': dataTestId,
  };

  // Add interactive attributes if clickable
  if (onClick) {
    props.role = 'button';
    props.tabIndex = 0;
    props.style = {
      ...paperStyle,
      cursor: 'pointer',
    };
  }

  // Filter out undefined props
  const filteredProps = Object.fromEntries(
    Object.entries(props).filter(([, value]) => value !== undefined)
  );

  return createElement(component, filteredProps, children);
};

NativePaper.displayName = 'NativePaper';

export default NativePaper;
