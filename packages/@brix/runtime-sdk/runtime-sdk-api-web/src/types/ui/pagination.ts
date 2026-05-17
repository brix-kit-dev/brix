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
 * @file Pagination Component Type Definitions
 * @description Defines types for the Pagination component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/pagination
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Pagination provides page navigation for large datasets
 * - Supports page size selection and quick jumpers
 * - Plugins must obtain Pagination through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { CSSProperties } from 'react';
import type { ComponentSize } from './common';

/**
 * Pagination Component Props
 *
 * Navigation component for paged data display.
 * Supports page navigation, page size selection, and total display.
 *
 * **Design Principle: Controlled Navigation**
 * Pagination follows controlled component pattern where page state
 * is managed externally and changes are communicated via callbacks.
 *
 * @example
 * ```tsx
 * const { Pagination, Table } = useUI();
 * const [page, setPage] = useState(1);
 * const [pageSize, setPageSize] = useState(10);
 *
 * // Basic pagination
 * <Pagination
 *   current={page}
 *   total={data.total}
 *   pageSize={pageSize}
 *   onChange={(newPage) => setPage(newPage)}
 * />
 *
 * // Pagination with page size selector
 * <Pagination
 *   current={page}
 *   total={data.total}
 *   pageSize={pageSize}
 *   showSizeChanger
 *   pageSizeOptions={[10, 20, 50, 100]}
 *   onChange={(newPage) => setPage(newPage)}
 *   onPageSizeChange={(newSize) => {
 *     setPageSize(newSize);
 *     setPage(1); // Reset to first page
 *   }}
 * />
 *
 * // Compact pagination with total
 * <Pagination
 *   current={page}
 *   total={data.total}
 *   pageSize={pageSize}
 *   showTotal={(total) => `Total ${total} items`}
 *   size="small"
 *   onChange={(newPage) => setPage(newPage)}
 * />
 * ```
 */
export interface PaginationProps {
  /**
   * Current Page
   *
   * The currently active page number (1-indexed).
   */
  current: number;

  /**
   * Total Items
   *
   * Total number of items across all pages.
   * Used to calculate total pages.
   */
  total: number;

  /**
   * Page Size
   *
   * Number of items displayed per page.
   * @default 10
   */
  pageSize?: number;

  /**
   * Page Size Options
   *
   * Available page size options for the size selector.
   * @default [10, 20, 50, 100]
   */
  pageSizeOptions?: number[];

  /**
   * Show Size Changer
   *
   * When true, displays a page size selector dropdown.
   * @default false
   */
  showSizeChanger?: boolean;

  /**
   * Show Quick Jumper
   *
   * When true, displays an input to jump to a specific page.
   * @default false
   */
  showQuickJumper?: boolean;

  /**
   * Show Total Function
   *
   * Function to render the total items display.
   * Receives total and current range as parameters.
   *
   * @param total - Total number of items
   * @param range - Current visible range [start, end]
   * @returns Display content for total
   *
   * @example
   * showTotal={(total, range) => `${range[0]}-${range[1]} of ${total}`}
   */
  showTotal?: (total: number, range: [number, number]) => React.ReactNode;

  /**
   * Pagination Size
   *
   * Controls the size of pagination controls.
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Simple Mode
   *
   * When true, shows simplified pagination (only prev/next with page input).
   * @default false
   */
  simple?: boolean;

  /**
   * Disabled State
   *
   * When true, all pagination controls are disabled.
   * @default false
   */
  disabled?: boolean;

  /**
   * Hide on Single Page
   *
   * When true, hides pagination when there's only one page.
   * @default false
   */
  hideOnSinglePage?: boolean;

  /**
   * Page Change Handler
   *
   * Callback fired when the page changes.
   *
   * @param page - The new page number
   */
  onChange: (page: number) => void;

  /**
   * Page Size Change Handler
   *
   * Callback fired when the page size changes.
   * Only triggered when showSizeChanger is true.
   *
   * @param pageSize - The new page size
   */
  onPageSizeChange?: (pageSize: number) => void;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the pagination container.
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
