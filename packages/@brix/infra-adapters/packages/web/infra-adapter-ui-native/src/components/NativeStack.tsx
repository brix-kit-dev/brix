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
 * @file Native Stack Component
 * @description Pure CSS implementation of StackProps from UIAdapter contract.
 *              Flexbox layout container for arranging children with consistent spacing.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeStack
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Uses CSS flexbox with gap property for spacing
 * - Semantic props (direction, spacing, align) abstract raw CSS
 * - Full cross-browser compatibility
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic layout component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for flex-based layouts.
 * Replaces direct MUI Stack usage in enterprise-solutions plugins.
 */

import { createElement, type FC, type CSSProperties, type ReactNode, Children } from 'react';
import type { StackProps } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Stack Component
 *
 * <p>Pure CSS implementation of StackProps from UIAdapter contract.
 * Provides a simplified flexbox API for arranging child elements with
 * consistent spacing and alignment.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS flexbox</li>
 *   <li>Direction, spacing, and alignment abstraction</li>
 *   <li>Flex wrap support for responsive layouts</li>
 *   <li>Optional dividers between children</li>
 *   <li>Full width mode for form layouts</li>
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
 * // Horizontal button group with spacing
 * const { Stack, Button } = useUI();
 *
 * <Stack direction="row" spacing={8}>
 *   <Button variant="secondary">Cancel</Button>
 *   <Button variant="primary">Submit</Button>
 * </Stack>
 *
 * // Vertical form layout
 * <Stack direction="column" spacing={16} align="stretch">
 *   <Input label="Name" />
 *   <Input label="Email" />
 *   <Button fullWidth>Register</Button>
 * </Stack>
 * ```
 *
 * @param props - StackProps from UIAdapter contract
 * @returns Native Stack component
 */
export const NativeStack: FC<StackProps> = ({
  direction = 'column',
  spacing = 0,
  align,
  justify,
  wrap = 'nowrap',
  divider = false,
  fullWidth = false,
  component = 'div',
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Build flexbox styles
  const stackStyle: CSSProperties = {
    display: 'flex',
    flexDirection: direction,
    gap: spacing,
    alignItems: align,
    justifyContent: justify,
    flexWrap: wrap,
    width: fullWidth ? '100%' : undefined,
    ...style,
  };

  // If dividers are enabled, we need to interleave them between children
  let content: ReactNode = children;
  
  if (divider) {
    const childArray = Children.toArray(children);
    const isVertical = direction === 'column' || direction === 'column-reverse';
    
    const dividerStyle: CSSProperties = {
      backgroundColor: 'rgba(0, 0, 0, 0.12)',
      alignSelf: 'stretch',
      flexShrink: 0,
      ...(isVertical
        ? { height: '1px', width: '100%' }
        : { width: '1px', height: 'auto' }),
    };

    content = childArray.reduce<ReactNode[]>((acc, child, index) => {
      if (index > 0) {
        acc.push(
          <div key={`divider-${index}`} style={dividerStyle} aria-hidden="true" />
        );
      }
      acc.push(child);
      return acc;
    }, []);
  }

  // Build props for createElement
  const props: Record<string, unknown> = {
    style: stackStyle,
    className,
    'data-testid': dataTestId,
  };

  // Filter out undefined props
  const filteredProps = Object.fromEntries(
    Object.entries(props).filter(([, value]) => value !== undefined)
  );

  return createElement(component, filteredProps, content);
};

NativeStack.displayName = 'NativeStack';

export default NativeStack;
