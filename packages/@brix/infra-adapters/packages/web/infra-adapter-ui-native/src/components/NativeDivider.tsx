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
 * @file Native Divider Component
 * @description Pure CSS implementation of DividerProps from UIAdapter contract.
 *              Visual separator for creating boundaries between content sections.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeDivider
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Supports horizontal and vertical orientations
 * - Optional text label with configurable alignment
 * - Flexbox integration via flexItem prop
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic layout component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for visual separation.
 * Replaces direct MUI Divider usage in enterprise-solutions plugins.
 */

import type { FC, CSSProperties } from 'react';
import type { DividerProps, DividerVariant, DividerTextAlign } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Style Constants
// ============================================================================

/**
 * Divider Color
 *
 * <p>Standard divider color matching Material Design spec.</p>
 */
const DIVIDER_COLOR = 'rgba(0, 0, 0, 0.12)';

/**
 * Inset Values
 *
 * <p>Margin values for different divider variants.</p>
 */
const VARIANT_INSET: Record<DividerVariant, { start: number; end: number }> = {
  fullWidth: { start: 0, end: 0 },
  inset: { start: 72, end: 0 },
  middle: { start: 16, end: 16 },
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Divider Component
 *
 * <p>Pure CSS implementation of DividerProps from UIAdapter contract.
 * Creates visual boundaries between content sections with optional
 * text labels.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS</li>
 *   <li>Horizontal and vertical orientations</li>
 *   <li>Text content with alignment options</li>
 *   <li>Variant-based inset styles</li>
 *   <li>Flexbox integration</li>
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
 * // Simple horizontal divider
 * const { Divider, Stack, Typography } = useUI();
 *
 * <Stack spacing={16}>
 *   <Typography>Section 1</Typography>
 *   <Divider />
 *   <Typography>Section 2</Typography>
 * </Stack>
 *
 * // Divider with text label
 * <Divider textAlign="center">OR</Divider>
 *
 * // Vertical divider in a row
 * <Stack direction="row" spacing={16}>
 *   <span>Left</span>
 *   <Divider orientation="vertical" flexItem />
 *   <span>Right</span>
 * </Stack>
 * ```
 *
 * @param props - DividerProps from UIAdapter contract
 * @returns Native Divider component
 */
export const NativeDivider: FC<DividerProps> = ({
  orientation = 'horizontal',
  variant = 'fullWidth',
  textAlign = 'center',
  flexItem = false,
  absolute = false,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  const isVertical = orientation === 'vertical';
  const hasContent = Boolean(children);
  const inset = VARIANT_INSET[variant];

  // Base divider styles
  const baseStyle: CSSProperties = {
    margin: 0,
    flexShrink: 0,
    borderStyle: 'solid',
    borderColor: DIVIDER_COLOR,
    ...style,
  };

  // Simple line divider (no content)
  if (!hasContent) {
    const lineStyle: CSSProperties = {
      ...baseStyle,
      ...(isVertical
        ? {
            borderWidth: '0 0 0 1px',
            height: flexItem ? 'auto' : '100%',
            alignSelf: flexItem ? 'stretch' : undefined,
            marginTop: inset?.start,
            marginBottom: inset?.end,
          }
        : {
            borderWidth: '0 0 1px 0',
            width: '100%',
            marginLeft: inset?.start,
            marginRight: inset?.end,
          }),
      ...(absolute
        ? {
            position: 'absolute',
            left: 0,
            right: 0,
            bottom: 0,
          }
        : {}),
    };

    return (
      <hr
        style={lineStyle}
        className={className}
        data-testid={dataTestId}
        aria-hidden="true"
      />
    );
  }

  // Divider with text content
  const containerStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    whiteSpace: 'nowrap',
    textAlign: 'center',
    fontSize: '14px',
    color: 'rgba(0, 0, 0, 0.6)',
    marginLeft: inset?.start,
    marginRight: inset?.end,
    ...style,
  };

  const lineBeforeStyle: CSSProperties = {
    flex: getFlexValue(textAlign, 'before'),
    borderTop: `1px solid ${DIVIDER_COLOR}`,
  };

  const lineAfterStyle: CSSProperties = {
    flex: getFlexValue(textAlign, 'after'),
    borderTop: `1px solid ${DIVIDER_COLOR}`,
  };

  const textStyle: CSSProperties = {
    display: 'inline-block',
    padding: '0 16px',
    flexShrink: 0,
  };

  return (
    <div
      style={containerStyle}
      className={className}
      data-testid={dataTestId}
      role="separator"
    >
      <span style={lineBeforeStyle} aria-hidden="true" />
      <span style={textStyle}>{children}</span>
      <span style={lineAfterStyle} aria-hidden="true" />
    </div>
  );
};

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Get Flex Value for Divider Lines
 *
 * <p>Calculates the flex grow value for lines before and after text content
 * based on the text alignment setting.</p>
 *
 * @param textAlign - Text alignment setting
 * @param position - 'before' or 'after' the text
 * @returns Flex grow value
 */
function getFlexValue(textAlign: DividerTextAlign, position: 'before' | 'after'): number {
  switch (textAlign) {
    case 'left':
      return position === 'before' ? 0.1 : 1;
    case 'right':
      return position === 'before' ? 1 : 0.1;
    case 'center':
    default:
      return 1;
  }
}

NativeDivider.displayName = 'NativeDivider';

export default NativeDivider;
