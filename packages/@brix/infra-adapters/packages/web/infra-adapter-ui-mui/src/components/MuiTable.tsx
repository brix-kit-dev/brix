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
 * @file MUI Table Component
 * @description Material UI implementation of TableProps from UIAdapter contract.
 *              Data table with sorting, selection, and custom rendering.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiTable
 * @version 3.2.0
 *
 * [Design Principles]
 * - Declarative column definitions with render functions
 * - Controlled sorting and selection
 * - Responsive with sticky header support
 * - Full accessibility via MUI Table
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic data display component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for data presentation.
 * Replaces direct MUI Table usage in enterprise-solutions plugins.
 *
 * [Implementation Notes]
 * - Uses MUI Table components under the hood
 * - Supports generic typing for row data
 * - Loading state overlays CircularProgress
 * - Empty state uses Empty component pattern
 */

import type { FC, Key, ReactNode } from 'react';
import { useMemo, useCallback } from 'react';
import type {
  TableProps,
  TableColumn,
  ComponentSize,
} from '@brix-sdk/runtime-sdk-api-web';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TableSortLabel from '@mui/material/TableSortLabel';
import Checkbox from '@mui/material/Checkbox';
import Radio from '@mui/material/Radio';
import CircularProgress from '@mui/material/CircularProgress';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

// ============================================================================
// Size Mapping
// ============================================================================

/**
 * Maps UIAdapter ComponentSize to MUI Table size
 */
const SIZE_MAP: Record<ComponentSize, 'small' | 'medium'> = {
  small: 'small',
  medium: 'medium',
  large: 'medium',
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
function getNestedValue(obj: unknown, path: string): unknown {
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
 * Extracts row key from record using rowKey prop
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
 * MUI Table Component
 *
 * <p>Material UI implementation of TableProps from UIAdapter contract.
 * Provides a declarative data table with sorting, selection, and
 * custom cell rendering.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Built on MUI Table for enterprise reliability</li>
 *   <li>Column-based declarative API</li>
 *   <li>Sortable columns with visual indicators</li>
 *   <li>Row selection with checkbox/radio modes</li>
 *   <li>Custom cell rendering via render functions</li>
 *   <li>Loading and empty states</li>
 *   <li>Sticky header support</li>
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
 * const { Table, Tag, Button } = useUI();
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
 *   rowSelection={{
 *     selectedRowKeys,
 *     onChange: setSelectedRowKeys
 *   }}
 * />
 * ```
 *
 * @param props - TableProps from UIAdapter contract
 * @returns MUI Table component
 */
export const MuiTable: FC<TableProps<any>> = ({
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

  // Calculate selected state for "select all" header checkbox
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

  // Handle "select all" checkbox toggle
  const handleSelectAll = useCallback(() => {
    if (!rowSelection?.onChange) return;

    if (isAllSelected) {
      // Deselect all
      rowSelection.onChange([], []);
    } else {
      // Select all
      rowSelection.onChange(allRowKeys, dataSource);
    }
  }, [isAllSelected, allRowKeys, dataSource, rowSelection]);

  // Handle individual row selection
  const handleRowSelect = useCallback(
    (record: any, key: Key) => {
      if (!rowSelection?.onChange) return;

      const newSelectedKeys = [...(rowSelection.selectedRowKeys || [])];
      const newSelectedRows = [...dataSource.filter((r: unknown) =>
        newSelectedKeys.includes(extractRowKey(r, rowKey))
      )];

      if (selectionType === 'radio') {
        // Radio: single selection replaces previous
        rowSelection.onChange([key], [record]);
      } else {
        // Checkbox: toggle selection
        const index = newSelectedKeys.indexOf(key);
        if (index > -1) {
          newSelectedKeys.splice(index, 1);
          const rowIndex = newSelectedRows.findIndex(
            (r) => extractRowKey(r, rowKey) === key
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

      // Cycle through: null -> ascend -> descend -> null
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

  // Render cell content using column render function or direct value
  const renderCell = useCallback(
    (column: TableColumn<any>, record: any, index: number): ReactNode => {
      const value = column.dataIndex
        ? getNestedValue(record, column.dataIndex as string)
        : undefined;

      if (column.render) {
        return column.render(value, record, index);
      }

      // Handle ellipsis
      if (column.ellipsis) {
        return (
          <Box
            component="span"
            sx={{
              display: 'block',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              maxWidth: column.width || '100%',
            }}
          >
            {value as ReactNode}
          </Box>
        );
      }

      return value as ReactNode;
    },
    []
  );

  // Container styles for sticky header and max height
  const containerSx = useMemo(
    () => ({
      maxHeight: maxHeight,
      ...style,
    }),
    [maxHeight, style]
  );

  // Render loading overlay
  const renderLoadingOverlay = () => {
    if (!loading) return null;

    return (
      <Box
        sx={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: 'rgba(255, 255, 255, 0.7)',
          zIndex: 10,
        }}
      >
        <CircularProgress />
      </Box>
    );
  };

  // Render empty state
  const renderEmptyState = () => {
    if (dataSource.length > 0 || loading) return null;

    const colSpan = columns.length + (hasSelection ? 1 : 0);

    return (
      <TableRow>
        <TableCell colSpan={colSpan} align="center">
          {emptyText || (
            <Typography color="textSecondary" sx={{ py: 4 }}>
              No Data
            </Typography>
          )}
        </TableCell>
      </TableRow>
    );
  };

  return (
    <Box sx={{ position: 'relative' }} className={className} data-testid={dataTestId}>
      {renderLoadingOverlay()}
      <TableContainer sx={containerSx}>
        <Table
          stickyHeader={stickyHeader}
          size={SIZE_MAP[size]}
          sx={{
            ...(bordered && {
              border: '1px solid',
              borderColor: 'divider',
              '& th, & td': {
                border: '1px solid',
                borderColor: 'divider',
              },
            }),
          }}
        >
          {/* Table Header */}
          <TableHead>
            <TableRow>
              {/* Selection column header */}
              {hasSelection && (
                <TableCell padding="checkbox">
                  {selectionType === 'checkbox' && (
                    <Checkbox
                      indeterminate={isIndeterminate}
                      checked={isAllSelected}
                      onChange={handleSelectAll}
                    />
                  )}
                </TableCell>
              )}

              {/* Data column headers */}
              {columns.map((column: TableColumn<unknown>) => (
                <TableCell
                  key={column.key}
                  align={column.align}
                  style={{
                    width: column.width,
                    minWidth: column.minWidth,
                    position: column.fixed ? 'sticky' : undefined,
                    left: column.fixed === 'left' ? 0 : undefined,
                    right: column.fixed === 'right' ? 0 : undefined,
                    backgroundColor: column.fixed ? 'background.paper' : undefined,
                    zIndex: column.fixed ? 2 : undefined,
                  }}
                >
                  {column.sortable ? (
                    <TableSortLabel
                      active={sortState?.columnKey === column.key && sortState.order !== null}
                      direction={
                        sortState?.columnKey === column.key
                          ? sortState.order === 'descend'
                            ? 'desc'
                            : 'asc'
                          : 'asc'
                      }
                      onClick={() => handleSort(column.key)}
                    >
                      {column.title}
                    </TableSortLabel>
                  ) : (
                    column.title
                  )}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>

          {/* Table Body */}
          <TableBody>
            {renderEmptyState()}
            {dataSource.map((record: unknown, index: number) => {
              const key = extractRowKey(record, rowKey);
              const isSelected = selectedSet.has(key);
              const checkboxProps = rowSelection?.getCheckboxProps?.(record);

              return (
                <TableRow
                  key={key}
                  hover
                  selected={isSelected}
                  onClick={
                    onRowClick ? () => onRowClick(record, index) : undefined
                  }
                  sx={{
                    cursor: onRowClick ? 'pointer' : undefined,
                    ...(striped &&
                      index % 2 === 1 && {
                        backgroundColor: 'action.hover',
                      }),
                  }}
                >
                  {/* Selection column */}
                  {hasSelection && (
                    <TableCell padding="checkbox">
                      {selectionType === 'checkbox' ? (
                        <Checkbox
                          checked={isSelected}
                          disabled={checkboxProps?.disabled}
                          onChange={() => handleRowSelect(record, key)}
                          onClick={(e) => e.stopPropagation()}
                        />
                      ) : (
                        <Radio
                          checked={isSelected}
                          disabled={checkboxProps?.disabled}
                          onChange={() => handleRowSelect(record, key)}
                          onClick={(e) => e.stopPropagation()}
                        />
                      )}
                    </TableCell>
                  )}

                  {/* Data cells */}
                  {columns.map((column: TableColumn<unknown>) => (
                    <TableCell
                      key={column.key}
                      align={column.align}
                      style={{
                        width: column.width,
                        minWidth: column.minWidth,
                        position: column.fixed ? 'sticky' : undefined,
                        left: column.fixed === 'left' ? 0 : undefined,
                        right: column.fixed === 'right' ? 0 : undefined,
                        backgroundColor: column.fixed ? 'background.paper' : undefined,
                        zIndex: column.fixed ? 1 : undefined,
                      }}
                    >
                      {renderCell(column, record, index)}
                    </TableCell>
                  ))}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default MuiTable;
