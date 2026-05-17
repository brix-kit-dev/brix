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
 * @file Native Empty Component
 * @description Pure CSS implementation of EmptyProps from UIAdapter contract.
 *              Placeholder component for empty data states.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeEmpty
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Built-in default and simple illustrations
 * - Customizable description and actions
 * - Centered layout with appropriate spacing
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic data display component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for empty state rendering.
 * Replaces custom empty state implementations in enterprise-solutions plugins.
 */

import type { FC, CSSProperties } from 'react';
import type { EmptyProps } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// SVG Illustrations
// ============================================================================

/**
 * Default Empty Illustration SVG
 *
 * <p>A box/document illustration for general empty states.</p>
 */
const DEFAULT_ILLUSTRATION = `
<svg viewBox="0 0 184 152" xmlns="http://www.w3.org/2000/svg">
  <g fill="none" fill-rule="evenodd">
    <g transform="translate(24 31.67)">
      <ellipse fill="#F5F5F7" cx="67.797" cy="106.89" rx="67.797" ry="12.668"/>
      <path d="M122.034 69.674L98.109 40.229c-1.148-1.386-2.826-2.225-4.593-2.225h-51.44c-1.766 0-3.444.839-4.592 2.225L13.56 69.674v15.383h108.475V69.674z" fill="#AEB8C2"/>
      <path d="M101.537 86.214L80.63 61.102c-1.001-1.207-2.507-1.867-4.048-1.867H31.724c-1.54 0-3.047.66-4.048 1.867L6.769 86.214v13.792h94.768V86.214z" fill="#DCE0E6" transform="translate(13.56)"/>
      <path d="M33.83 0h67.933a4 4 0 0 1 4 4v93.344H29.83V4a4 4 0 0 1 4-4z" fill="#F5F5F7"/>
      <path d="M42.678 9.953h50.237a2 2 0 0 1 2 2V36.91a2 2 0 0 1-2 2H42.678a2 2 0 0 1-2-2V11.953a2 2 0 0 1 2-2zM42.94 49.767h49.713a2.262 2.262 0 1 1 0 4.524H42.94a2.262 2.262 0 0 1 0-4.524zM42.94 61.53h49.713a2.262 2.262 0 1 1 0 4.525H42.94a2.262 2.262 0 0 1 0-4.525zM121.813 105.032c-.775 3.071-3.497 5.36-6.735 5.36H20.515c-3.238 0-5.96-2.29-6.734-5.36a7.309 7.309 0 0 1-.222-1.79V69.675h26.318c2.907 0 5.25 2.448 5.25 5.42v.04c0 2.971 2.37 5.37 5.277 5.37h34.785c2.907 0 5.277-2.421 5.277-5.393V75.1c0-2.972 2.343-5.426 5.25-5.426h26.318v33.569c0 .617-.077 1.216-.221 1.789z" fill="#DCE0E6"/>
    </g>
    <path d="M149.121 33.292l-6.83 2.65a1 1 0 0 1-1.317-1.23l1.937-6.207c-2.589-2.944-4.109-6.534-4.109-10.408C138.802 8.102 148.92 0 161.402 0 173.881 0 184 8.102 184 18.097c0 9.995-10.118 18.097-22.599 18.097-4.528 0-8.744-1.066-12.28-2.902z" fill="#DCE0E6"/>
    <g transform="translate(149.65 15.383)" fill="#FFF">
      <ellipse cx="20.654" cy="3.167" rx="2.849" ry="2.815"/>
      <path d="M5.698 5.63H0L2.898.704zM9.259.704h4.985V5.63H9.259z"/>
    </g>
  </g>
</svg>
`;

/**
 * Simple Empty Illustration SVG
 *
 * <p>A minimal illustration for compact empty states.</p>
 */
const SIMPLE_ILLUSTRATION = `
<svg viewBox="0 0 64 41" xmlns="http://www.w3.org/2000/svg">
  <g transform="translate(0 1)" fill="none" fill-rule="evenodd">
    <ellipse fill="#F5F5F5" cx="32" cy="33" rx="32" ry="7"/>
    <g fill-rule="nonzero" stroke="#D9D9D9">
      <path d="M55 12.76L44.854 1.258C44.367.474 43.656 0 42.907 0H21.093c-.749 0-1.46.474-1.947 1.257L9 12.761V22h46v-9.24z"/>
      <path d="M41.613 15.931c0-1.605.994-2.93 2.227-2.931H55v18.137C55 33.26 53.68 35 52.05 35h-40.1C10.32 35 9 33.259 9 31.137V13h11.16c1.233 0 2.227 1.323 2.227 2.928v.022c0 1.605 1.005 2.901 2.237 2.901h14.752c1.232 0 2.237-1.308 2.237-2.913v-.007z" fill="#FAFAFA"/>
    </g>
  </g>
</svg>
`;

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Empty Component
 *
 * <p>Pure CSS implementation of EmptyProps from UIAdapter contract.
 * Displays a placeholder for empty data states with optional
 * illustration, description, and action slots.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS/SVG</li>
 *   <li>Built-in default and simple illustrations</li>
 *   <li>Custom image support via ReactNode</li>
 *   <li>Description text slot</li>
 *   <li>Action button slot via children</li>
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
 * const { Empty, Button } = useUI();
 *
 * // Basic empty state
 * <Empty description="No data available" />
 *
 * // Empty state with action
 * <Empty
 *   image="simple"
 *   description="No items found"
 * >
 *   <Button onClick={handleCreate}>Create New</Button>
 * </Empty>
 * ```
 *
 * @param props - EmptyProps from UIAdapter contract
 * @returns Native Empty component
 */
export const NativeEmpty: FC<EmptyProps> = ({
  image = 'default',
  imageStyle,
  description,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Container styles
  const containerStyle: CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '32px 8px',
    textAlign: 'center',
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    ...style,
  };

  // Image container styles
  const imageContainerStyle: CSSProperties = {
    marginBottom: 8,
    height: image === 'simple' ? 35 : 100,
    ...imageStyle,
  };

  // Description styles
  const descriptionStyle: CSSProperties = {
    margin: '8px 0',
    color: 'rgba(0, 0, 0, 0.45)',
    fontSize: '14px',
    lineHeight: 1.57,
  };

  // Footer styles (for children/actions)
  const footerStyle: CSSProperties = {
    marginTop: 24,
  };

  // Determine which image to render
  const renderImage = () => {
    // Custom ReactNode image
    if (typeof image !== 'string') {
      return <div style={imageContainerStyle}>{image}</div>;
    }

    // Built-in illustrations
    const svgContent = image === 'simple' ? SIMPLE_ILLUSTRATION : DEFAULT_ILLUSTRATION;

    return (
      <div
        style={imageContainerStyle}
        dangerouslySetInnerHTML={{ __html: svgContent }}
      />
    );
  };

  return (
    <div
      style={containerStyle}
      className={className}
      data-testid={dataTestId}
      role="status"
      aria-label="Empty"
    >
      {/* Illustration */}
      {image !== null && renderImage()}

      {/* Description */}
      {description !== undefined && (
        <div style={descriptionStyle}>
          {description}
        </div>
      )}

      {/* Actions */}
      {children && <div style={footerStyle}>{children}</div>}
    </div>
  );
};

NativeEmpty.displayName = 'NativeEmpty';

export default NativeEmpty;
