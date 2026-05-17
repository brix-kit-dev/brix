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
 * @file Native Breadcrumb Component
 * @description Pure CSS implementation of BreadcrumbProps from UIAdapter contract.
 *              Hierarchical navigation path indicator.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeBreadcrumb
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Customizable separator
 * - Support for both items array and children patterns
 * - Clickable navigation links
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic navigation component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for breadcrumb trails.
 * Replaces direct MUI Breadcrumbs usage in enterprise-solutions plugins.
 */

import type { FC, CSSProperties, ReactNode, Children as ReactChildren } from 'react';
import { Children, isValidElement } from 'react';
import type { BreadcrumbProps, BreadcrumbItemProps } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// BreadcrumbItem Component
// ============================================================================

/**
 * Native BreadcrumbItem Component
 *
 * <p>Individual item in a breadcrumb trail.
 * Can be a link or plain text (last item).</p>
 *
 * @example
 * ```tsx
 * <BreadcrumbItem href="/home">Home</BreadcrumbItem>
 * <BreadcrumbItem onClick={() => navigate('/users')}>Users</BreadcrumbItem>
 * <BreadcrumbItem>Current Page</BreadcrumbItem>
 * ```
 *
 * @param props - BreadcrumbItemProps from UIAdapter contract
 * @returns Native BreadcrumbItem component
 */
export const NativeBreadcrumbItem: FC<BreadcrumbItemProps> = ({
  href,
  onClick,
  icon,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Is this item interactive (link or clickable)?
  const isInteractive = href || onClick;

  // Item styles
  const itemStyle: CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 4,
    color: isInteractive ? '#1976d2' : 'rgba(0, 0, 0, 0.87)',
    textDecoration: 'none',
    cursor: isInteractive ? 'pointer' : 'default',
    fontSize: '14px',
    transition: 'color 0.2s ease',
    ...style,
  };

  // Handle click
  const handleClick = (e: React.MouseEvent) => {
    if (onClick) {
      e.preventDefault();
      onClick(e);
    }
  };

  // Render as link or span
  if (href) {
    return (
      <a
        href={href}
        onClick={onClick ? handleClick : undefined}
        style={itemStyle}
        className={className}
        data-testid={dataTestId}
        onMouseEnter={(e) => {
          e.currentTarget.style.color = '#1565c0';
          e.currentTarget.style.textDecoration = 'underline';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.color = '#1976d2';
          e.currentTarget.style.textDecoration = 'none';
        }}
      >
        {icon && <span>{icon}</span>}
        {children}
      </a>
    );
  }

  if (onClick) {
    return (
      <span
        role="button"
        tabIndex={0}
        onClick={handleClick}
        onKeyDown={(e) => e.key === 'Enter' && onClick(e as unknown as React.MouseEvent)}
        style={itemStyle}
        className={className}
        data-testid={dataTestId}
        onMouseEnter={(e) => {
          e.currentTarget.style.color = '#1565c0';
          e.currentTarget.style.textDecoration = 'underline';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.color = '#1976d2';
          e.currentTarget.style.textDecoration = 'none';
        }}
      >
        {icon && <span>{icon}</span>}
        {children}
      </span>
    );
  }

  // Non-interactive (typically the last item)
  return (
    <span style={itemStyle} className={className} data-testid={dataTestId}>
      {icon && <span>{icon}</span>}
      {children}
    </span>
  );
};

NativeBreadcrumbItem.displayName = 'NativeBreadcrumbItem';

// ============================================================================
// Breadcrumb Component
// ============================================================================

/**
 * Native Breadcrumb Component
 *
 * <p>Pure CSS implementation of BreadcrumbProps from UIAdapter contract.
 * Displays a hierarchical navigation path.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS</li>
 *   <li>Customizable separator (string or ReactNode)</li>
 *   <li>Support for items array or BreadcrumbItem children</li>
 *   <li>Optional item renderer for custom displays</li>
 *   <li>Responsive ellipsis for long paths</li>
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
 * const { Breadcrumb, BreadcrumbItem } = useUI();
 *
 * // Using items array
 * <Breadcrumb
 *   items={[
 *     { title: 'Home', href: '/' },
 *     { title: 'Users', href: '/users' },
 *     { title: 'John Doe' },
 *   ]}
 * />
 *
 * // Using children
 * <Breadcrumb separator=">">
 *   <BreadcrumbItem href="/">Home</BreadcrumbItem>
 *   <BreadcrumbItem href="/products">Products</BreadcrumbItem>
 *   <BreadcrumbItem>Details</BreadcrumbItem>
 * </Breadcrumb>
 * ```
 *
 * @param props - BreadcrumbProps from UIAdapter contract
 * @returns Native Breadcrumb component
 */
export const NativeBreadcrumb: FC<BreadcrumbProps> = ({
  items,
  separator = '/',
  itemRender,
  params,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Container styles
  const containerStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    flexWrap: 'wrap',
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    fontSize: '14px',
    lineHeight: 1.5,
    ...style,
  };

  // Separator styles
  const separatorStyle: CSSProperties = {
    margin: '0 8px',
    color: 'rgba(0, 0, 0, 0.45)',
    userSelect: 'none',
  };

  // Build breadcrumb items from items array or children
  let breadcrumbItems: ReactNode[] = [];

  if (items && items.length > 0) {
    // Using items array
    breadcrumbItems = items.map((item, index) => {
      const isLast = index === items.length - 1;

      // Custom renderer
      if (itemRender) {
        return itemRender(item, params, items, [item.title as string]);
      }

      // Default rendering
      return (
        <NativeBreadcrumbItem
          key={`${item.title}-${index}`}
          href={!isLast ? item.href : undefined}
          onClick={!isLast ? item.onClick : undefined}
          icon={item.icon}
        >
          {item.title}
        </NativeBreadcrumbItem>
      );
    });
  } else if (children) {
    // Using children
    breadcrumbItems = Children.toArray(children).filter(isValidElement);
  }

  // Interleave separator between items
  const renderWithSeparators = () => {
    const result: ReactNode[] = [];

    breadcrumbItems.forEach((item, index) => {
      // Add item
      result.push(<span key={`item-${index}`}>{item}</span>);

      // Add separator (except after last item)
      if (index < breadcrumbItems.length - 1) {
        result.push(
          <span key={`sep-${index}`} style={separatorStyle} aria-hidden="true">
            {separator}
          </span>
        );
      }
    });

    return result;
  };

  return (
    <nav
      aria-label="Breadcrumb"
      style={containerStyle}
      className={className}
      data-testid={dataTestId}
    >
      {renderWithSeparators()}
    </nav>
  );
};

NativeBreadcrumb.displayName = 'NativeBreadcrumb';

export default NativeBreadcrumb;
