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
 * @file MUI Empty Component
 * @description Material UI implementation of EmptyProps from UIAdapter contract.
 *              Empty state placeholder for data-less scenarios.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiEmpty
 * @version 3.2.0
 *
 * [Design Principles]
 * - Styled empty state matching Material Design guidelines
 * - Custom image or predefined illustration support
 * - Action slot for call-to-action buttons
 * - Responsive centering and spacing
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic feedback component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for empty states.
 * MUI does not have a native Empty component, so this is a custom implementation.
 */

import type { FC, ReactNode } from 'react';
import type { EmptyProps } from '@brix-sdk/runtime-sdk-api-web';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

// ============================================================================
// Default Empty Illustration
// ============================================================================

/**
 * Default empty state SVG illustration
 *
 * <p>A simple box/inbox icon representing "no data".</p>
 */
const DefaultEmptyImage: FC<{ style?: React.CSSProperties }> = ({ style }) => (
  <svg
    style={{
      width: 64,
      height: 41,
      margin: '0 auto',
      ...style,
    }}
    viewBox="0 0 64 41"
    xmlns="http://www.w3.org/2000/svg"
  >
    <g transform="translate(0 1)" fill="none" fillRule="evenodd">
      <ellipse fill="#f5f5f5" cx="32" cy="33" rx="32" ry="7" />
      <g fillRule="nonzero" stroke="#d9d9d9">
        <path d="M55 12.76L44.854 1.258C44.367.474 43.656 0 42.907 0H21.093c-.749 0-1.46.474-1.947 1.257L9 12.761V22h46v-9.24z" />
        <path
          d="M41.613 15.931c0-1.605.994-2.93 2.227-2.931H55v18.137C55 33.26 53.68 35 52.05 35H11.95C10.32 35 9 33.259 9 31.137V13h11.16c1.233 0 2.227 1.323 2.227 2.928v.022c0 1.605 1.005 2.901 2.237 2.901h14.752c1.232 0 2.237-1.308 2.237-2.913v-.007z"
          fill="#fafafa"
        />
      </g>
    </g>
  </svg>
);

/**
 * Simple empty state SVG illustration
 *
 * <p>A minimalist circle representing "no data".</p>
 */
const SimpleEmptyImage: FC<{ style?: React.CSSProperties }> = ({ style }) => (
  <svg
    style={{
      width: 64,
      height: 64,
      margin: '0 auto',
      ...style,
    }}
    viewBox="0 0 64 64"
    xmlns="http://www.w3.org/2000/svg"
  >
    <circle
      cx="32"
      cy="32"
      r="30"
      fill="none"
      stroke="#d9d9d9"
      strokeWidth="2"
      strokeDasharray="8 4"
    />
    <text
      x="32"
      y="38"
      textAnchor="middle"
      fill="#bfbfbf"
      fontSize="14"
      fontFamily="Arial, sans-serif"
    >
      Empty
    </text>
  </svg>
);

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Empty Component
 *
 * <p>Material UI implementation of EmptyProps from UIAdapter contract.
 * Provides a placeholder component for empty data states with
 * customizable illustrations and call-to-action elements.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Predefined default and simple illustrations</li>
 *   <li>Custom image/icon support</li>
 *   <li>Customizable description text</li>
 *   <li>Action slot for CTA buttons</li>
 *   <li>Centered, responsive layout</li>
 * </ul>
 *
 * <h3>Architectural Constraints:</h3>
 * <ul>
 *   <li>This component is an atomic building block</li>
 *   <li>Shell layer uses this via UIAdapter interface</li>
 *   <li>No direct import allowed in Plugin layer</li>
 *   <li>Custom implementation as MUI lacks native Empty</li>
 * </ul>
 *
 * @example
 * ```tsx
 * const { Empty, Button } = useUI();
 *
 * // Basic empty state
 * <Empty description="No data available" />
 *
 * // Empty state with action
 * <Empty
 *   description="No items found"
 *   image="simple"
 * >
 *   <Button variant="primary" onClick={handleCreate}>
 *     Create First Item
 *   </Button>
 * </Empty>
 *
 * // Custom image
 * <Empty
 *   image={<Icon name="search" style={{ fontSize: 48, color: '#ccc' }} />}
 *   description={`No results for "${searchTerm}"`}
 * />
 * ```
 *
 * @param props - EmptyProps from UIAdapter contract
 * @returns Custom empty state component
 */
export const MuiEmpty: FC<EmptyProps> = ({
  image = 'default',
  imageStyle,
  description = 'No Data',
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Render appropriate image based on prop type
  const renderImage = (): ReactNode => {
    if (typeof image === 'string') {
      // Predefined image type
      switch (image) {
        case 'simple':
          return <SimpleEmptyImage style={imageStyle} />;
        case 'default':
        default:
          return <DefaultEmptyImage style={imageStyle} />;
      }
    }
    // Custom ReactNode image
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', ...imageStyle }}>
        {image}
      </Box>
    );
  };

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        textAlign: 'center',
        padding: 4,
        ...style,
      }}
      className={className}
      data-testid={dataTestId}
    >
      {/* Image/Illustration */}
      <Box sx={{ marginBottom: 2 }}>{renderImage()}</Box>

      {/* Description Text */}
      {description && (
        <Typography
          variant="body2"
          color="textSecondary"
          sx={{ marginBottom: children ? 2 : 0 }}
        >
          {description}
        </Typography>
      )}

      {/* Action Slot */}
      {children && <Box>{children}</Box>}
    </Box>
  );
};

export default MuiEmpty;
