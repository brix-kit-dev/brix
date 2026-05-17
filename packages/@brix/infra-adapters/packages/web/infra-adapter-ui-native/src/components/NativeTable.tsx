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
 * @file Native Table Component
 * @description Pure CSS implementation of TableProps from UIAdapter contract.
 *              Data table with sorting, selection, and custom cell rendering.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeTable
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Declarative column-based API with render functions
 * - Controlled sorting and selection patterns
 * - Loading and empty state handling
 * - Sticky header support
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic data display component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for data presentation.
 * Replaces direct MUI Table usage in enterprise-solutions plugins.
 */

import type { FC, Key, ReactNode, CSSProperties } from 'react';
import { useMemo, useCallback } from 'react';
import type { TableProps, TableColumn, ComponentSize } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Size Mappings
// ============================================================================

/**
 * Cell Padding Sizes
 *
 * <p>Maps ComponentSize to cell padding values.</p>
 */
const SIZE_PADDING: Record<ComponentSize, string> = {
  small: '6px 16px',
  medium: '12px 16px',
  large: '16px 16px',
};

/**
 * Font Sizes
 *
 * <p>Maps ComponentSize to font size values.</p>
 */
const SIZE_FONT: Record<ComponentSize, string> = {
  small: '13px',
  medium: '14px',
  large: '15px',
};

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Gets value from nested object path
 *
 * @param obj - Source object
 * @param path - Dot-notated path string
 * @returns Value at path or undefined
 */
function getNestedValue(obj: unknown, path: string | undefined): unknown {
  if (!path) return undefined;

  const keys = path.split('.');
  let current: unknown = obj;

  for (const key of keys) {
    if (current === null || current === undefined) return undefined;
    current = (current as Record<string, unknown>)[key];
  }

  return current;
}

/**
 * Extracts row key from record
 *
 * @param record - Row data object
 * @param rowKey - Key string or extractor function
 * @returns Row key value
 */
function extractRowKey<T>(
  record: T,
  rowKey: keyof T | ((record: T) => Key)
): Key {
  if (typeof rowKey === 'function') {
    return rowKey(record);
  }
  return (record as Record<string, unknown>)[rowKey as string] as Key;
}

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Table Component
 *
 * <p>Pure CSS implementation of TableProps from UIAdapter contract.
 * Provides a declarative data table with sorting, selection, and
 * custom cell rendering capabilities.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure HTML/CSS table</li>
 *   <li>Column-based declarative API</li>
 *   <li>Sortable columns with visual indicators</li>
 *   <li>Row selection (checkbox/radio modes)</li>
 *   <li>Custom cell rendering via render functions</li>
 *   <li>Loading and empty states</li>
 *   <li>Sticky header support</li>
 *   <li>Striped and bordered variants</li>
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
 * const { Table, Tag } = useUI();
 *
 * const columns = [
 *   { key: 'name', title: 'Name', dataIndex: 'name', sortable: true },
 *   {
 *     key: 'status',
 *     title: 'Status',
 *     dataIndex: 'status',
 *     render: (value) => <Tag color={value === 'active' ? 'success' : 'default'}>{value}</Tag>
 *   }
 * ];
 *
 * <Table
 *   columns={columns}
 *   dataSource={users}
 *   rowKey="id"
 *   loading={isLoading}
 * />
 * ```
 *
 * @param props - TableProps from UIAdapter contract
 * @returns Native Table component
 */
export const NativeTable: FC<TableProps<any>> = ({
  columns,
  dataSource,
  rowKey,
  loading = false,
  size = 'medium',
  bordered = false,
  striped = false,
  stickyHeader = false,
  maxHeight,
  rowSelection,
  sortState,
  onSort,
  onRowClick,
  emptyText,
  style,
  className,
  'data-testid': dataTestId,
}) => {
  // Determine if any rows are selectable
  const hasSelection = Boolean(rowSelection);
  const selectionType = rowSelection?.type || 'checkbox';

  // Calculate selected state
  const allRowKeys = useMemo(
    () => dataSource.map((record: unknown) => extractRowKey(record, rowKey)),
    [dataSource, rowKey]
  );

  const selectedSet = useMemo(
    () => new Set(rowSelection?.selectedRowKeys || []),
    [rowSelection?.selectedRowKeys]
  );

  const isAllSelected =
    allRowKeys.length > 0 && allRowKeys.every((key: Key) => selectedSet.has(key));

  const isIndeterminate =
    !isAllSelected && allRowKeys.some((key: Key) => selectedSet.has(key));

  // Handle "select all" toggle
  const handleSelectAll = useCallback(() => {
    if (!rowSelection?.onChange) return;

    if (isAllSelected) {
      rowSelection.onChange([], []);
    } else {
      rowSelection.onChange(allRowKeys, dataSource);
    }
  }, [isAllSelected, allRowKeys, dataSource, rowSelection]);

  // Handle individual row selection
  const handleRowSelect = useCallback(
    (record: any, key: Key) => {
      if (!rowSelection?.onChange) return;

      if (selectionType === 'radio') {
        rowSelection.onChange([key], [record]);
      } else {
        const newSelectedKeys = [...(rowSelection.selectedRowKeys || [])];
        const newSelectedRows = dataSource.filter((r: unknown) =>
          newSelectedKeys.includes(extractRowKey(r, rowKey))
        );

        const index = newSelectedKeys.indexOf(key);
        if (index > -1) {
          newSelectedKeys.splice(index, 1);
          const rowIndex = newSelectedRows.findIndex(
            (r: any) => extractRowKey(r, rowKey) === key
          );
          if (rowIndex > -1) newSelectedRows.splice(rowIndex, 1);
        } else {
          newSelectedKeys.push(key);
          newSelectedRows.push(record);
        }
        rowSelection.onChange(newSelectedKeys, newSelectedRows);
      }
    },
    [rowSelection, dataSource, rowKey, selectionType]
  );

  // Handle column sort click
  const handleSort = useCallback(
    (columnKey: string) => {
      if (!onSort) return;

      const currentOrder = sortState?.columnKey === columnKey ? sortState.order : null;
      let newOrder: 'ascend' | 'descend' | null;

      if (currentOrder === null) {
        newOrder = 'ascend';
      } else if (currentOrder === 'ascend') {
        newOrder = 'descend';
      } else {
        newOrder = null;
      }

      onSort({ columnKey, order: newOrder });
    },
    [onSort, sortState]
  );

  // Render cell content
  const renderCell = useCallback(
    (column: TableColumn<any>, record: any, index: number): ReactNode => {
      const value = column.dataIndex
        ? getNestedValue(record, column.dataIndex as string)
        : undefined;

      if (column.render) {
        return column.render(value, record, index);
      }

      if (column.ellipsis) {
        return (
          <div
            style={{
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {String(value ?? '')}
          </div>
        );
      }

      return String(value ?? '');
    },
    []
  );

  // Table styles
  const containerStyle: CSSProperties = {
    width: '100%',
    overflow: 'auto',
    maxHeight: maxHeight,
    position: 'relative',
    ...style,
  };

  const tableStyle: CSSProperties = {
    width: '100%',
    borderCollapse: 'collapse',
    borderSpacing: 0,
    fontSize: SIZE_FONT[size],
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
  };

  const thStyle: CSSProperties = {
    padding: SIZE_PADDING[size],
    textAlign: 'left',
    fontWeight: 500,
    color: 'rgba(0, 0, 0, 0.87)',
    backgroundColor: '#fafafa',
    borderBottom: '1px solid rgba(0, 0, 0, 0.12)',
    position: stickyHeader ? 'sticky' : undefined,
    top: stickyHeader ? 0 : undefined,
    zIndex: stickyHeader ? 1 : undefined,
    ...(bordered ? { border: '1px solid rgba(0, 0, 0, 0.12)' } : {}),
  };

  const tdStyle: CSSProperties = {
    padding: SIZE_PADDING[size],
    borderBottom: '1px solid rgba(0, 0, 0, 0.12)',
    color: 'rgba(0, 0, 0, 0.87)',
    ...(bordered ? { border: '1px solid rgba(0, 0, 0, 0.12)' } : {}),
  };

  const getRowStyle = (index: number): CSSProperties => ({
    backgroundColor: striped && index % 2 === 1 ? 'rgba(0, 0, 0, 0.02)' : undefined,
    cursor: onRowClick ? 'pointer' : undefined,
    transition: 'background-color 0.2s',
  });

  // Loading overlay
  const loadingOverlay = loading && (
    <div
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(255, 255, 255, 0.7)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 2,
      }}
    >
      <div
        style={{
          width: 32,
          height: 32,
          border: '3px solid rgba(0, 0, 0, 0.1)',
          borderTopColor: '#1976d2',
          borderRadius: '50%',
          animation: 'spin 1s linear infinite',
        }}
      />
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );

  // Empty state
  const isEmpty = dataSource.length === 0;
  const emptyState = isEmpty && !loading && (
    <tr>
      <td
        colSpan={columns.length + (hasSelection ? 1 : 0)}
        style={{
          padding: '48px 16px',
          textAlign: 'center',
          color: 'rgba(0, 0, 0, 0.38)',
        }}
      >
        {emptyText || 'No data'}
      </td>
    </tr>
  );

  return (
    <div style={containerStyle} className={className} data-testid={dataTestId}>
      {loadingOverlay}
      <table style={tableStyle}>
        <thead>
          <tr>
            {/* Selection column header */}
            {hasSelection && (
              <th style={{ ...thStyle, width: 48, textAlign: 'center' }}>
                {selectionType === 'checkbox' && (
                  <input
                    type="checkbox"
                    checked={isAllSelected}
                    ref={(el) => {
                      if (el) el.indeterminate = isIndeterminate;
                    }}
                    onChange={handleSelectAll}
                    style={{ cursor: 'pointer' }}
                  />
                )}
              </th>
            )}
            {/* Column headers */}
            {columns.map((column: TableColumn<any>) => (
              <th
                key={column.key}
                style={{
                  ...thStyle,
                  width: column.width,
                  minWidth: column.minWidth,
                  textAlign: column.align || 'left',
                  cursor: column.sortable ? 'pointer' : undefined,
                  userSelect: column.sortable ? 'none' : undefined,
                }}
                onClick={() => column.sortable && handleSort(column.key)}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  {column.title}
                  {/* Sort indicator */}
                  {column.sortable && sortState?.columnKey === column.key && (
                    <span style={{ fontSize: 12 }}>
                      {sortState.order === 'ascend' ? '▲' : '▼'}
                    </span>
                  )}
                </div>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {emptyState}
          {dataSource.map((record: any, index: number) => {
            const key = extractRowKey(record, rowKey);
            const isSelected = selectedSet.has(key);

            return (
              <tr
                key={String(key)}
                style={{
                  ...getRowStyle(index),
                  backgroundColor: isSelected
                    ? 'rgba(25, 118, 210, 0.08)'
                    : getRowStyle(index).backgroundColor,
                }}
                onClick={() => onRowClick?.(record, index)}
              >
                {/* Selection cell */}
                {hasSelection && (
                  <td style={{ ...tdStyle, textAlign: 'center' }}>
                    <input
                      type={selectionType}
                      checked={isSelected}
                      onChange={() => handleRowSelect(record, key)}
                      onClick={(e) => e.stopPropagation()}
                      style={{ cursor: 'pointer' }}
                    />
                  </td>
                )}
                {/* Data cells */}
                {columns.map((column: TableColumn<any>) => (
                  <td
                    key={column.key}
                    style={{
                      ...tdStyle,
                      textAlign: column.align || 'left',
                    }}
                  >
                    {renderCell(column, record, index)}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};

NativeTable.displayName = 'NativeTable';

export default NativeTable;
