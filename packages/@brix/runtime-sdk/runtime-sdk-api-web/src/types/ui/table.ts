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
 * @file Table Component Type Definitions
 * @description Defines types for the Table data display component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/table
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Table provides a data-driven table with column definitions
 * - Supports sorting, selection, and custom cell rendering
 * - Plugins must obtain Table through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 *
 * [Design Philosophy]
 * The Table component uses a declarative column-based API where data structure
 * and presentation are separated. This allows for flexible customization while
 * maintaining a consistent interface across different UI library implementations.
 */

import type { ReactNode, CSSProperties, Key } from 'react';
import type { ComponentSize } from './common';

/**
 * Table Column Definition
 *
 * Defines the structure and behavior of a single table column.
 * Generic type T represents the row data type for type-safe field access.
 *
 * @template T - The type of data object for each row
 */
export interface TableColumn<T = unknown> {
  /**
   * Column Key
   *
   * Unique identifier for the column.
   * Used for internal operations like sorting and selection.
   */
  key: string;

  /**
   * Column Title
   *
   * Header text displayed at the top of the column.
   */
  title: ReactNode;

  /**
   * Data Field Key
   *
   * Property name in the row data object to display.
   * Can be a nested path using dot notation (e.g., 'user.name').
   */
  dataIndex?: keyof T | string;

  /**
   * Column Width
   *
   * Fixed width for the column in pixels or CSS value.
   */
  width?: number | string;

  /**
   * Minimum Width
   *
   * Minimum width constraint for the column.
   */
  minWidth?: number;

  /**
   * Text Alignment
   *
   * Horizontal alignment of cell content.
   * @default 'left'
   */
  align?: 'left' | 'center' | 'right';

  /**
   * Sortable Flag
   *
   * When true, enables sorting by this column.
   * @default false
   */
  sortable?: boolean;

  /**
   * Fixed Position
   *
   * Fixes the column to the left or right edge of the table.
   * Fixed columns remain visible during horizontal scrolling.
   */
  fixed?: 'left' | 'right';

  /**
   * Ellipsis Mode
   *
   * When true, truncates cell content with ellipsis when it overflows.
   * @default false
   */
  ellipsis?: boolean;

  /**
   * Custom Cell Renderer
   *
   * Function to render custom cell content.
   * Receives the cell value, row data, and row index.
   *
   * @param value - The cell value from dataIndex
   * @param record - The complete row data object
   * @param index - The row index in the data array
   * @returns The rendered cell content
   */
  render?: (value: unknown, record: T, index: number) => ReactNode;
}

/**
 * Sort Order
 *
 * Current sorting direction for a column.
 */
export type SortOrder = 'ascend' | 'descend' | null;

/**
 * Table Sort State
 *
 * Represents the current sorting state of the table.
 */
export interface TableSortState {
  /**
   * Column Key
   *
   * The key of the column being sorted.
   */
  columnKey: string;

  /**
   * Sort Order
   *
   * The current sort direction.
   */
  order: SortOrder;
}

/**
 * Row Selection Configuration
 *
 * Configuration for table row selection behavior.
 *
 * @template T - The type of data object for each row
 */
export interface TableRowSelection<T = unknown> {
  /**
   * Selection Type
   *
   * - checkbox: Multiple selection with checkboxes
   * - radio: Single selection with radio buttons
   *
   * @default 'checkbox'
   */
  type?: 'checkbox' | 'radio';

  /**
   * Selected Row Keys
   *
   * Array of keys for currently selected rows.
   * Use with onChange for controlled selection.
   */
  selectedRowKeys?: Key[];

  /**
   * Selection Change Handler
   *
   * Callback fired when selection changes.
   *
   * @param selectedRowKeys - Array of selected row keys
   * @param selectedRows - Array of selected row data objects
   */
  onChange?: (selectedRowKeys: Key[], selectedRows: T[]) => void;

  /**
   * Row Selection Predicate
   *
   * Function to determine if a row can be selected.
   * Returns the disabled state for the selection checkbox/radio.
   *
   * @param record - The row data object
   * @returns Props for the selection control (typically { disabled: boolean })
   */
  getCheckboxProps?: (record: T) => { disabled?: boolean; name?: string };
}

/**
 * Table Component Props
 *
 * Data table component with declarative column definitions.
 * Supports sorting, selection, pagination, and custom rendering.
 *
 * **Design Principle: Declarative Data Display**
 * Uses a column definition array to describe table structure,
 * separating data from presentation for flexibility.
 *
 * @template T - The type of data object for each row
 *
 * @example
 * ```tsx
 * const { Table, Tag, Button } = useUI();
 *
 * interface User {
 *   id: string;
 *   name: string;
 *   email: string;
 *   status: 'active' | 'inactive';
 * }
 *
 * const columns: TableColumn<User>[] = [
 *   { key: 'name', title: 'Name', dataIndex: 'name', sortable: true },
 *   { key: 'email', title: 'Email', dataIndex: 'email', ellipsis: true },
 *   {
 *     key: 'status',
 *     title: 'Status',
 *     dataIndex: 'status',
 *     render: (value) => (
 *       <Tag color={value === 'active' ? 'success' : 'default'}>
 *         {value}
 *       </Tag>
 *     )
 *   },
 *   {
 *     key: 'actions',
 *     title: 'Actions',
 *     align: 'center',
 *     render: (_, record) => (
 *       <Button size="small" onClick={() => handleEdit(record)}>
 *         Edit
 *       </Button>
 *     )
 *   }
 * ];
 *
 * <Table
 *   columns={columns}
 *   dataSource={users}
 *   rowKey="id"
 *   loading={isLoading}
 *   rowSelection={{
 *     selectedRowKeys,
 *     onChange: setSelectedRowKeys
 *   }}
 *   onSort={(sortState) => handleSort(sortState)}
 * />
 * ```
 */
export interface TableProps<T = unknown> {
  /**
   * Column Definitions
   *
   * Array of column configuration objects defining table structure.
   */
  columns: TableColumn<T>[];

  /**
   * Data Source
   *
   * Array of data objects to display as rows.
   */
  dataSource: T[];

  /**
   * Row Key
   *
   * Property name or function to extract unique key for each row.
   * Required for selection and efficient re-rendering.
   *
   * @example
   * rowKey="id"
   * rowKey={(record) => record.id}
   */
  rowKey: keyof T | ((record: T) => Key);

  /**
   * Loading State
   *
   * When true, displays a loading indicator over the table.
   * @default false
   */
  loading?: boolean;

  /**
   * Table Size
   *
   * Controls the density of the table rows.
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Bordered Style
   *
   * When true, displays borders around cells.
   * @default false
   */
  bordered?: boolean;

  /**
   * Striped Rows
   *
   * When true, alternates row background colors.
   * @default false
   */
  striped?: boolean;

  /**
   * Sticky Header
   *
   * When true, keeps the header fixed during vertical scroll.
   * @default false
   */
  stickyHeader?: boolean;

  /**
   * Maximum Height
   *
   * Sets a maximum height with vertical scrolling.
   * Use with stickyHeader for better UX.
   */
  maxHeight?: number | string;

  /**
   * Row Selection Configuration
   *
   * Enables row selection with checkboxes or radio buttons.
   */
  rowSelection?: TableRowSelection<T>;

  /**
   * Sort State
   *
   * Current sorting state for controlled sorting.
   */
  sortState?: TableSortState;

  /**
   * Sort Change Handler
   *
   * Callback fired when sorting changes.
   *
   * @param sortState - The new sort state
   */
  onSort?: (sortState: TableSortState) => void;

  /**
   * Row Click Handler
   *
   * Callback fired when a row is clicked.
   *
   * @param record - The clicked row data
   * @param index - The row index
   */
  onRowClick?: (record: T, index: number) => void;

  /**
   * Empty State Content
   *
   * Custom content to display when dataSource is empty.
   */
  emptyText?: ReactNode;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the table container.
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
