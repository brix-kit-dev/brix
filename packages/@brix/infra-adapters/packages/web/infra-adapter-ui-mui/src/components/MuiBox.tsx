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
 * @file MUI Box Component
 * @description Material UI implementation of BoxProps from UIAdapter contract.
 *              Universal layout container serving as the foundational building block.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiBox
 * @version 3.2.0
 *
 * [Design Principles]
 * - Direct mapping from BoxProps to MUI Box API
 * - Supports component polymorphism for semantic HTML
 * - Standard CSS properties via style prop for cross-library compatibility
 * - Full accessibility support with ARIA attributes
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic layout component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for layout composition.
 * Replaces direct div + sx usage in enterprise-solutions plugins.
 */

import type { FC } from 'react';
import type { BoxProps } from '@brix-sdk/runtime-sdk-api-web';
import Box from '@mui/material/Box';

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Box Component
 *
 * <p>Material UI implementation of BoxProps from UIAdapter contract.
 * Serves as the universal layout container for building complex layouts
 * through composition.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Built on MUI Box for consistent styling</li>
 *   <li>Component polymorphism via 'component' prop</li>
 *   <li>Standard CSS properties via style prop</li>
 *   <li>Full accessibility support (ARIA, tabIndex, role)</li>
 *   <li>Test ID support for e2e testing</li>
 * </ul>
 *
 * <h3>Architectural Constraints:</h3>
 * <ul>
 *   <li>This component is an atomic building block</li>
 *   <li>Layout components use this via UIAdapter interface</li>
 *   <li>No direct import allowed in Plugin layer</li>
 *   <li>Style prop must use standard CSS, not MUI sx</li>
 * </ul>
 *
 * @example
 * ```tsx
 * // Basic usage via useUI hook
 * const { Box } = useUI();
 *
 * <Box style={{ padding: 16, marginBottom: 8 }}>
 *   Content here
 * </Box>
 *
 * // As a flex container
 * <Box
 *   style={{
 *     display: 'flex',
 *     justifyContent: 'space-between',
 *     alignItems: 'center'
 *   }}
 * >
 *   <span>Left</span>
 *   <span>Right</span>
 * </Box>
 *
 * // Semantic HTML with component prop
 * <Box component="section" aria-label="Main content">
 *   <Box component="article">Article content</Box>
 * </Box>
 * ```
 *
 * @param props - BoxProps from UIAdapter contract
 * @returns MUI Box component
 */
export const MuiBox: FC<BoxProps> = ({
  component = 'div',
  style,
  className,
  id,
  onClick,
  tabIndex,
  role,
  'aria-label': ariaLabel,
  'aria-labelledby': ariaLabelledBy,
  'data-testid': dataTestId,
  children,
}) => {
  return (
    <Box
      component={component}
      sx={style}
      className={className}
      id={id}
      onClick={onClick}
      tabIndex={tabIndex}
      role={role}
      aria-label={ariaLabel}
      aria-labelledby={ariaLabelledBy}
      data-testid={dataTestId}
    >
      {children}
    </Box>
  );
};

export default MuiBox;
