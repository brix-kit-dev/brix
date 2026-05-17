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
 * @file Spin Component Type Definitions
 * @description Defines types for the Spin loading component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/spin
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Spin provides loading indicator for async operations
 * - Wraps MUI CircularProgress / Ant Design Spin internally
 * - Plugins must obtain Spin through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 *
 * [Naming Convention]
 * This component is named 'Spin' to align with Ant Design naming.
 * MUI implementations will wrap CircularProgress internally.
 */

import type { ReactNode, CSSProperties } from 'react';
import type { ComponentSize } from './common';

/**
 * Spin Component Props
 *
 * Loading indicator component for displaying async operation status.
 * Can be used standalone or as a wrapper to overlay content.
 *
 * **Design Principle: Loading Feedback**
 * Spin provides visual feedback during data fetching or processing,
 * preventing user confusion and maintaining perceived responsiveness.
 *
 * @example
 * ```tsx
 * const { Spin, Stack, Card } = useUI();
 *
 * // Standalone spinner
 * {isLoading && <Spin />}
 *
 * // Spinner with tip text
 * <Spin spinning={isLoading} tip="Loading data...">
 *   <Card>
 *     {data && <DataDisplay data={data} />}
 *   </Card>
 * </Spin>
 *
 * // Different sizes
 * <Stack direction="row" spacing={16} align="center">
 *   <Spin size="small" />
 *   <Spin size="medium" />
 *   <Spin size="large" />
 * </Stack>
 *
 * // Loading indicator for a section
 * <Spin spinning={isFetching}>
 *   <Table columns={columns} dataSource={data} />
 * </Spin>
 *
 * // Full screen loading overlay
 * <Spin spinning={isSubmitting} fullScreen tip="Saving changes..." />
 * ```
 */
export interface SpinProps {
  /**
   * Spinning State
   *
   * When true, displays the loading indicator.
   * When wrapping content, also applies overlay effect.
   *
   * @default true
   */
  spinning?: boolean;

  /**
   * Spin Size
   *
   * Controls the spinner dimensions.
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Loading Tip
   *
   * Text displayed below or beside the spinner.
   * Use to provide context about the loading operation.
   */
  tip?: ReactNode;

  /**
   * Tip Alignment
   *
   * Position of the tip text relative to the spinner.
   * @default 'bottom'
   */
  tipAlign?: 'top' | 'right' | 'bottom' | 'left';

  /**
   * Delay Time
   *
   * Delay in milliseconds before showing the spinner.
   * Use to prevent flash for fast operations.
   * Only applies when spinning becomes true.
   */
  delay?: number;

  /**
   * Custom Indicator
   *
   * Custom spinner element to replace the default.
   * Can be an icon or any ReactNode.
   */
  indicator?: ReactNode;

  /**
   * Full Screen Mode
   *
   * When true, displays spinner in a full-screen overlay.
   * Use for blocking operations that affect the entire page.
   *
   * @default false
   */
  fullScreen?: boolean;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the spin container.
   */
  style?: CSSProperties;

  /**
   * Wrapper Styles
   *
   * CSS properties applied to the nested content wrapper.
   * Only applicable when wrapping children.
   */
  wrapperStyle?: CSSProperties;

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

  /**
   * Wrapped Content
   *
   * Content to overlay with the loading indicator.
   * When provided, Spin acts as a wrapper component.
   */
  children?: ReactNode;
}
