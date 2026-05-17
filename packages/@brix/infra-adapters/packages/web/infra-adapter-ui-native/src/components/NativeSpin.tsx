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
 * @file Native Spin Component
 * @description Pure CSS implementation of SpinProps from UIAdapter contract.
 *              Loading indicator with optional content wrapper.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeSpin
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Pure CSS animation for spinner
 * - Wraps content with semi-transparent overlay when spinning
 * - Optional tip text below spinner
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic feedback component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for loading states.
 * Replaces direct MUI CircularProgress usage in enterprise-solutions plugins.
 */

import type { FC, CSSProperties } from 'react';
import type { SpinProps } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Size Mappings
// ============================================================================

/**
 * Spinner Size Mappings
 *
 * <p>Dimensions for small, medium, and large spinners.</p>
 */
const SIZE_MAP: Record<string, number> = {
  small: 16,
  medium: 24,
  large: 40,
};

// ============================================================================
// Spin Component
// ============================================================================

/**
 * Native Spin Component
 *
 * <p>Pure CSS implementation of SpinProps from UIAdapter contract.
 * Displays a loading spinner, optionally wrapping content.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS animation</li>
 *   <li>Three sizes: small, medium, large</li>
 *   <li>Controlled spinning state</li>
 *   <li>Optional tip text</li>
 *   <li>Can wrap content with overlay</li>
 *   <li>Custom indicator support</li>
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
 * const { Spin } = useUI();
 *
 * // Standalone spinner
 * <Spin spinning={loading} />
 *
 * // Spinner with tip text
 * <Spin spinning={loading} tip="Loading data..." />
 *
 * // Wrapped content with loading overlay
 * <Spin spinning={loading}>
 *   <Table dataSource={data} columns={columns} />
 * </Spin>
 * ```
 *
 * @param props - SpinProps from UIAdapter contract
 * @returns Native Spin component
 */
export const NativeSpin: FC<SpinProps> = ({
  spinning = true,
  size = 'medium',
  tip,
  indicator,
  delay,
  wrapperClassName,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Get spinner dimensions
  const spinnerSize = typeof size === 'number' ? size : SIZE_MAP[size] || SIZE_MAP.medium;

  // Unique ID for keyframes
  const keyframesId = 'native-spin-keyframes';

  // Spinner container styles
  const spinnerContainerStyle: CSSProperties = {
    display: 'inline-flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: 8,
    ...style,
  };

  // Spinner animation styles
  const spinnerStyle: CSSProperties = {
    width: spinnerSize,
    height: spinnerSize,
    border: `${Math.max(2, spinnerSize / 10)}px solid rgba(25, 118, 210, 0.2)`,
    borderTopColor: '#1976d2',
    borderRadius: '50%',
    animation: 'native-spin 0.8s linear infinite',
    boxSizing: 'border-box',
  };

  // Tip text styles
  const tipStyle: CSSProperties = {
    fontSize: size === 'small' ? 12 : size === 'large' ? 16 : 14,
    color: '#1976d2',
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
  };

  // Render just the spinner (for standalone or overlay)
  const renderSpinner = () => (
    <div
      style={spinnerContainerStyle}
      className={className}
      data-testid={dataTestId}
      data-size={size}
    >
      {/* Keyframes style */}
      <style>{`
        @keyframes native-spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
      `}</style>

      {/* Spinner indicator */}
      {indicator || <div style={spinnerStyle} />}

      {/* Tip text */}
      {tip && <span style={tipStyle}>{tip}</span>}
    </div>
  );

  // If no children, render standalone spinner
  if (!children) {
    return spinning ? renderSpinner() : null;
  }

  // Wrapper styles for content
  const wrapperStyle: CSSProperties = {
    position: 'relative',
  };

  // Overlay styles
  const overlayStyle: CSSProperties = {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.75)',
    zIndex: 10,
    transition: 'opacity 0.3s ease',
    opacity: spinning ? 1 : 0,
    pointerEvents: spinning ? 'auto' : 'none',
  };

  // Content styles (blur when spinning)
  const contentStyle: CSSProperties = {
    transition: 'filter 0.3s ease, opacity 0.3s ease',
    filter: spinning ? 'blur(0.5px)' : 'none',
    opacity: spinning ? 0.5 : 1,
    pointerEvents: spinning ? 'none' : 'auto',
  };

  return (
    <div style={wrapperStyle} className={wrapperClassName}>
      {/* Content */}
      <div style={contentStyle}>{children}</div>

      {/* Overlay with spinner */}
      <div style={overlayStyle}>{renderSpinner()}</div>
    </div>
  );
};

NativeSpin.displayName = 'NativeSpin';

export default NativeSpin;
