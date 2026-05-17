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
 * @file Progress Component Type Definitions
 * @description Defines types for the Progress indicator component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/progress
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Progress displays operation completion status
 * - Supports linear and circular variants
 * - Plugins must obtain Progress through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties } from 'react';
import type { ComponentSize } from './common';

/**
 * Progress Type
 *
 * Visual shape of the progress indicator.
 * - line: Horizontal bar (default)
 * - circle: Circular progress
 * - dashboard: Semi-circular dashboard style
 */
export type ProgressType = 'line' | 'circle' | 'dashboard';

/**
 * Progress Status
 *
 * Visual status indicating progress state.
 * - normal: Default blue progress
 * - success: Green, operation completed
 * - error: Red, operation failed
 * - active: Animated stripe effect (line only)
 */
export type ProgressStatus = 'normal' | 'success' | 'error' | 'active';

/**
 * Progress Component Props
 *
 * Component for displaying operation completion percentage.
 * Supports determinate (percentage-based) and indeterminate modes.
 *
 * **Design Principle: Progress Feedback**
 * Progress indicators provide quantitative feedback about ongoing
 * operations, helping users understand completion status and wait time.
 *
 * @example
 * ```tsx
 * const { Progress, Stack } = useUI();
 *
 * // Basic linear progress
 * <Progress percent={45} />
 *
 * // Progress with status
 * <Stack spacing={16}>
 *   <Progress percent={100} status="success" />
 *   <Progress percent={70} status="error" />
 *   <Progress percent={50} status="active" />
 * </Stack>
 *
 * // Circular progress
 * <Progress type="circle" percent={75} />
 *
 * // Custom size circular
 * <Progress
 *   type="circle"
 *   percent={80}
 *   size={80}
 *   strokeWidth={8}
 * />
 *
 * // Progress with custom format
 * <Progress
 *   percent={uploadProgress}
 *   format={(percent) => `${percent}% uploaded`}
 * />
 *
 * // Hide percentage text
 * <Progress percent={30} showInfo={false} />
 * ```
 */
export interface ProgressProps {
  /**
   * Progress Type
   *
   * Visual shape of the progress indicator.
   * @default 'line'
   */
  type?: ProgressType;

  /**
   * Completion Percentage
   *
   * Current progress value from 0 to 100.
   * @default 0
   */
  percent?: number;

  /**
   * Progress Status
   *
   * Visual status affecting color and animation.
   * @default 'normal'
   */
  status?: ProgressStatus;

  /**
   * Show Percentage Info
   *
   * When true, displays the percentage text.
   * @default true
   */
  showInfo?: boolean;

  /**
   * Progress Size
   *
   * Size preset for the progress indicator.
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Circle/Dashboard Width
   *
   * Width in pixels for circle and dashboard types.
   * Also controls height for these types.
   */
  width?: number;

  /**
   * Stroke Width
   *
   * Thickness of the progress bar/circle stroke.
   * @default 8 for line, 6 for circle
   */
  strokeWidth?: number;

  /**
   * Stroke Color
   *
   * Custom color for the progress bar.
   * Can be a single color or gradient definition.
   */
  strokeColor?: string | { from: string; to: string };

  /**
   * Trail Color
   *
   * Background track color.
   */
  trailColor?: string;

  /**
   * Stroke Line Cap
   *
   * Shape of the progress bar ends.
   * @default 'round'
   */
  strokeLinecap?: 'round' | 'butt' | 'square';

  /**
   * Custom Format Function
   *
   * Function to customize the percentage display.
   *
   * @param percent - Current percentage value
   * @returns Custom display content
   */
  format?: (percent?: number) => ReactNode;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the progress container.
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   *
   * Additional CSS class names for styling customization.
   */
  className?: string;

  /**
   * Test ID
   *
   * Data attribute for testing frameworks.
   */
  'data-testid'?: string;
}
