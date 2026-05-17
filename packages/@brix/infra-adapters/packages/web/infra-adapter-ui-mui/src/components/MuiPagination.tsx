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
 * @file MUI Pagination Component
 * @description Material UI implementation of PaginationProps from UIAdapter contract.
 *              Page navigation for large datasets with size selector.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiPagination
 * @version 3.2.0
 *
 * [Design Principles]
 * - Direct mapping from PaginationProps to MUI Pagination API
 * - Controlled component pattern for page state
 * - Optional page size selector using MUI Select
 * - Total display and quick jumper support
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic navigation component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for paginated data.
 * Replaces direct MUI Pagination usage in enterprise-solutions plugins.
 */

import type { FC } from 'react';
import { useMemo, useCallback } from 'react';
import type { PaginationProps, ComponentSize } from '@brix-sdk/runtime-sdk-api-web';
import MuiPaginationComponent from '@mui/material/Pagination';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

// ============================================================================
// Size Mapping
// ============================================================================

/**
 * Maps UIAdapter ComponentSize to MUI Pagination size
 */
const SIZE_MAP: Record<ComponentSize, 'small' | 'medium' | 'large'> = {
  small: 'small',
  medium: 'medium',
  large: 'large',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Pagination Component
 *
 * <p>Material UI implementation of PaginationProps from UIAdapter contract.
 * Provides page navigation controls with optional page size selector.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Built on MUI Pagination for consistent styling</li>
 *   <li>Page size selector dropdown</li>
 *   <li>Quick jumper input</li>
 *   <li>Total items display</li>
 *   <li>Simple mode for compact display</li>
 *   <li>Hide on single page option</li>
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
 * // With page size selector and total display
 * <Pagination
 *   current={page}
 *   total={data.total}
 *   pageSize={pageSize}
 *   showSizeChanger
 *   showTotal={(total) => `Total ${total} items`}
 *   onChange={(newPage) => setPage(newPage)}
 *   onPageSizeChange={(newSize) => setPageSize(newSize)}
 * />
 * ```
 *
 * @param props - PaginationProps from UIAdapter contract
 * @returns MUI Pagination with additional controls
 */
export const MuiPagination: FC<PaginationProps> = ({
  current,
  total,
  pageSize = 10,
  pageSizeOptions = [10, 20, 50, 100],
  showSizeChanger = false,
  showQuickJumper = false,
  showTotal,
  size = 'medium',
  simple = false,
  disabled = false,
  hideOnSinglePage = false,
  onChange,
  onPageSizeChange,
  style,
  className,
  'data-testid': dataTestId,
}) => {
  // Calculate total pages
  const totalPages = useMemo(
    () => Math.ceil(total / pageSize),
    [total, pageSize]
  );

  // Calculate current range for showTotal
  const currentRange = useMemo((): [number, number] => {
    const start = (current - 1) * pageSize + 1;
    const end = Math.min(current * pageSize, total);
    return [start, end];
  }, [current, pageSize, total]);

  // Hide if single page and option enabled
  if (hideOnSinglePage && totalPages <= 1) {
    return null;
  }

  // Handle page change from MUI Pagination
  const handlePageChange = useCallback(
    (_event: React.ChangeEvent<unknown>, page: number) => {
      onChange(page);
    },
    [onChange]
  );

  // Handle page size change
  const handlePageSizeChange = useCallback(
    (event: any) => {
      const newSize = Number(event.target.value);
      onPageSizeChange?.(newSize);
    },
    [onPageSizeChange]
  );

  // Handle quick jumper input
  const handleQuickJump = useCallback(
    (event: React.KeyboardEvent<HTMLInputElement>) => {
      if (event.key === 'Enter') {
        const value = parseInt((event.target as HTMLInputElement).value, 10);
        if (!isNaN(value) && value >= 1 && value <= totalPages) {
          onChange(value);
          (event.target as HTMLInputElement).value = '';
        }
      }
    },
    [onChange, totalPages]
  );

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'flex-end',
        flexWrap: 'wrap',
        gap: 2,
        ...style,
      }}
      className={className}
      data-testid={dataTestId}
    >
      {/* Total Display */}
      {showTotal && (
        <Typography variant="body2" color="textSecondary">
          {showTotal(total, currentRange)}
        </Typography>
      )}

      {/* Main Pagination */}
      <MuiPaginationComponent
        count={totalPages}
        page={current}
        onChange={handlePageChange}
        size={SIZE_MAP[size]}
        disabled={disabled}
        showFirstButton={!simple}
        showLastButton={!simple}
        siblingCount={simple ? 0 : 1}
        boundaryCount={simple ? 0 : 1}
      />

      {/* Page Size Selector */}
      {showSizeChanger && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Select
            value={pageSize}
            onChange={handlePageSizeChange}
            size="small"
            disabled={disabled}
            sx={{ minWidth: 80 }}
          >
            {pageSizeOptions.map((option: number) => (
              <MenuItem key={option} value={option}>
                {option} / page
              </MenuItem>
            ))}
          </Select>
        </Box>
      )}

      {/* Quick Jumper */}
      {showQuickJumper && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography variant="body2" color="textSecondary">
            Go to
          </Typography>
          <TextField
            type="number"
            size="small"
            disabled={disabled}
            inputProps={{
              min: 1,
              max: totalPages,
              style: { width: 50, textAlign: 'center' },
            }}
            onKeyDown={handleQuickJump}
          />
        </Box>
      )}
    </Box>
  );
};

export default MuiPagination;
