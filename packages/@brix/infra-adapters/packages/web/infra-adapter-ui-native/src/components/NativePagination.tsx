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
 * @file Native Pagination Component
 * @description Pure CSS implementation of PaginationProps from UIAdapter contract.
 *              Navigation component for paged data display.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativePagination
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Page navigation with ellipsis for large datasets
 * - Page size selector with customizable options
 * - Controlled component pattern
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic data display component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for paginated data.
 * Replaces direct MUI Pagination usage in enterprise-solutions plugins.
 */

import { useState, useMemo, type FC, type CSSProperties } from 'react';
import type { PaginationProps, ComponentSize } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Size Mappings
// ============================================================================

/**
 * Button Size Styles
 *
 * <p>Maps ComponentSize to button dimensions.</p>
 */
const SIZE_STYLES: Record<ComponentSize, { height: number; fontSize: string; padding: string }> = {
  small: { height: 26, fontSize: '12px', padding: '0 8px' },
  medium: { height: 32, fontSize: '14px', padding: '0 10px' },
  large: { height: 40, fontSize: '16px', padding: '0 12px' },
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Pagination Component
 *
 * <p>Pure CSS implementation of PaginationProps from UIAdapter contract.
 * Provides page navigation controls for large datasets with customizable
 * page size options.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS</li>
 *   <li>Page number navigation with ellipsis</li>
 *   <li>Previous/Next buttons</li>
 *   <li>Page size selector</li>
 *   <li>Total items display</li>
 *   <li>Quick page jumper</li>
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
 * const { Pagination } = useUI();
 *
 * <Pagination
 *   current={page}
 *   total={totalItems}
 *   pageSize={pageSize}
 *   onChange={(page) => setPage(page)}
 *   showSizeChanger
 *   onPageSizeChange={(size) => setPageSize(size)}
 * />
 * ```
 *
 * @param props - PaginationProps from UIAdapter contract
 * @returns Native Pagination component
 */
export const NativePagination: FC<PaginationProps> = ({
  current,
  total,
  pageSize = 10,
  pageSizeOptions = [10, 20, 50, 100],
  showSizeChanger = false,
  showQuickJumper = false,
  showTotal,
  size = 'medium',
  onChange,
  onPageSizeChange,
  style,
  className,
  'data-testid': dataTestId,
}) => {
  // Quick jumper input state
  const [jumpValue, setJumpValue] = useState('');

  // Calculate total pages
  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  // Calculate visible page range (1-indexed)
  const range: [number, number] = [
    Math.min((current - 1) * pageSize + 1, total),
    Math.min(current * pageSize, total),
  ];

  // Generate page numbers with ellipsis
  const pageNumbers = useMemo(() => {
    const pages: (number | 'ellipsis')[] = [];
    const delta = 2; // Pages to show around current

    if (totalPages <= 7) {
      // Show all pages if total is small
      for (let i = 1; i <= totalPages; i++) pages.push(i);
    } else {
      // Always show first page
      pages.push(1);

      // Calculate range around current
      const rangeStart = Math.max(2, current - delta);
      const rangeEnd = Math.min(totalPages - 1, current + delta);

      // Add ellipsis before range if needed
      if (rangeStart > 2) {
        pages.push('ellipsis');
      }

      // Add pages in range
      for (let i = rangeStart; i <= rangeEnd; i++) {
        pages.push(i);
      }

      // Add ellipsis after range if needed
      if (rangeEnd < totalPages - 1) {
        pages.push('ellipsis');
      }

      // Always show last page
      pages.push(totalPages);
    }

    return pages;
  }, [current, totalPages]);

  // Handle page change
  const handlePageChange = (page: number) => {
    if (page >= 1 && page <= totalPages && page !== current) {
      onChange?.(page);
    }
  };

  // Handle page size change
  const handlePageSizeChange = (newSize: number) => {
    onPageSizeChange?.(newSize);
    // Reset to page 1 when page size changes
    onChange?.(1);
  };

  // Handle quick jump
  const handleQuickJump = () => {
    const page = parseInt(jumpValue, 10);
    if (!isNaN(page) && page >= 1 && page <= totalPages) {
      handlePageChange(page);
      setJumpValue('');
    }
  };

  // Get size-specific styles
  const sizeStyle = SIZE_STYLES[size] || SIZE_STYLES.medium;

  // Container styles
  const containerStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    fontSize: sizeStyle.fontSize,
    ...style,
  };

  // Button base styles
  const buttonBaseStyle: CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: sizeStyle.height,
    height: sizeStyle.height,
    padding: sizeStyle.padding,
    border: '1px solid rgba(0, 0, 0, 0.23)',
    borderRadius: '4px',
    backgroundColor: '#ffffff',
    color: 'rgba(0, 0, 0, 0.87)',
    cursor: 'pointer',
    transition: 'all 0.2s',
    outline: 'none',
    fontFamily: 'inherit',
    fontSize: 'inherit',
  };

  const activeButtonStyle: CSSProperties = {
    ...buttonBaseStyle,
    backgroundColor: '#1976d2',
    borderColor: '#1976d2',
    color: '#ffffff',
  };

  const disabledButtonStyle: CSSProperties = {
    ...buttonBaseStyle,
    cursor: 'not-allowed',
    opacity: 0.5,
  };

  // Select styles
  const selectStyle: CSSProperties = {
    height: sizeStyle.height,
    padding: sizeStyle.padding,
    border: '1px solid rgba(0, 0, 0, 0.23)',
    borderRadius: '4px',
    backgroundColor: '#ffffff',
    fontSize: 'inherit',
    cursor: 'pointer',
  };

  // Input styles for quick jumper
  const inputStyle: CSSProperties = {
    width: '50px',
    height: sizeStyle.height,
    padding: '0 8px',
    border: '1px solid rgba(0, 0, 0, 0.23)',
    borderRadius: '4px',
    textAlign: 'center',
    fontSize: 'inherit',
  };

  return (
    <div
      style={containerStyle}
      className={className}
      data-testid={dataTestId}
      role="navigation"
      aria-label="Pagination"
    >
      {/* Total display */}
      {showTotal && (
        <span style={{ marginRight: 8 }}>
          {showTotal(total, range)}
        </span>
      )}

      {/* Previous button */}
      <button
        style={current === 1 ? disabledButtonStyle : buttonBaseStyle}
        disabled={current === 1}
        onClick={() => handlePageChange(current - 1)}
        aria-label="Previous page"
      >
        ‹
      </button>

      {/* Page numbers */}
      {pageNumbers.map((page, index) => {
        if (page === 'ellipsis') {
          return (
            <span
              key={`ellipsis-${index}`}
              style={{ padding: '0 4px' }}
              aria-hidden="true"
            >
              …
            </span>
          );
        }

        const isActive = page === current;
        return (
          <button
            key={page}
            style={isActive ? activeButtonStyle : buttonBaseStyle}
            onClick={() => handlePageChange(page)}
            aria-label={`Page ${page}`}
            aria-current={isActive ? 'page' : undefined}
          >
            {page}
          </button>
        );
      })}

      {/* Next button */}
      <button
        style={current === totalPages ? disabledButtonStyle : buttonBaseStyle}
        disabled={current === totalPages}
        onClick={() => handlePageChange(current + 1)}
        aria-label="Next page"
      >
        ›
      </button>

      {/* Page size changer */}
      {showSizeChanger && (
        <select
          style={selectStyle}
          value={pageSize}
          onChange={(e) => handlePageSizeChange(Number(e.target.value))}
          aria-label="Items per page"
        >
          {pageSizeOptions.map((option: number) => (
            <option key={option} value={option}>
              {option} / page
            </option>
          ))}
        </select>
      )}

      {/* Quick jumper */}
      {showQuickJumper && (
        <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
          <span>Go to</span>
          <input
            style={inputStyle}
            type="number"
            min={1}
            max={totalPages}
            value={jumpValue}
            onChange={(e) => setJumpValue(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleQuickJump()}
            onBlur={handleQuickJump}
            aria-label="Go to page"
          />
        </span>
      )}
    </div>
  );
};

NativePagination.displayName = 'NativePagination';

export default NativePagination;
