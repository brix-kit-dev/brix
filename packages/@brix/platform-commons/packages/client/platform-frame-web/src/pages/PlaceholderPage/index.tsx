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
 * @file Placeholder Page Component
 * @description Placeholder page for features under development - Shell layer implementation
 * @module @brix-sdk/platform-frame-web/pages/PlaceholderPage
 * @version 3.2.0
 *
 * [Architecture Position]
 * This component belongs to Shell layer (Layer 2.5), providing pre-assembled placeholder pages.
 * Host layer imports and uses directly, following Host ultra-thin principle.
 *
 * [Design Principles]
 * - Consistent visual style with other Shell pages
 * - Support branding customization
 * - Support i18n (through props injection)
 * - Pure presentation component, no business logic
 *
 * @since 3.2.0
 */

import { useMemo, type CSSProperties, type ReactNode } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Placeholder Page Props
 *
 * Configuration for placeholder page display.
 *
 * @since 3.2.0
 */
export interface PlaceholderPageProps {
  /**
   * Page title
   *
   * If not provided, displays "Coming Soon"
   */
  title?: string;

  /**
   * Page description
   *
   * Detailed description shown below the title
   */
  description?: string;

  /**
   * Icon to display
   *
   * Can be emoji, icon class name, or React node
   * @default '🚧'
   */
  icon?: string | ReactNode;

  /**
   * Current pathname
   *
   * Displayed in code block for debugging
   */
  pathname?: string;

  /**
   * Status badge text
   *
   * @default 'Coming Soon'
   */
  statusText?: string;

  /**
   * Branding configuration
   *
   * Customizes colors and styling
   */
  branding?: {
    /** Primary color for accent elements */
    primaryColor?: string;
    /** Background color */
    backgroundColor?: string;
  };

  /**
   * Additional CSS class name
   */
  className?: string;

  /**
   * Additional inline styles
   */
  style?: CSSProperties;
}

/**
 * Simple Placeholder Page Config
 *
 * Factory function configuration for creating placeholder page components.
 *
 * @since 3.2.0
 */
export interface SimplePlaceholderConfig {
  /**
   * Default title for all placeholder pages
   */
  defaultTitle?: string;

  /**
   * Default description
   */
  defaultDescription?: string;

  /**
   * Default icon
   */
  defaultIcon?: string | ReactNode;

  /**
   * Default status text
   */
  defaultStatusText?: string;

  /**
   * Branding configuration
   */
  branding?: PlaceholderPageProps['branding'];
}

// ============================================================================
// Styles
// ============================================================================

/**
 * 【样式工厂函数】
 * 创建占位页面的样式对象，使用 useMemo 缓存以优化性能
 *
 * [Style Factory]
 * Creates style objects for placeholder page, using useMemo for performance optimization.
 */
function useStyles(primaryColor: string, backgroundColor?: string) {
  const containerStyle = useMemo<CSSProperties>(
    () => ({
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '400px',
      padding: '48px 24px',
      textAlign: 'center',
      backgroundColor: backgroundColor || 'transparent',
    }),
    [backgroundColor]
  );

  const iconStyle = useMemo<CSSProperties>(
    () => ({
      fontSize: '64px',
      marginBottom: '24px',
      opacity: 0.85,
      lineHeight: 1,
    }),
    []
  );

  const titleStyle = useMemo<CSSProperties>(
    () => ({
      fontSize: '24px',
      fontWeight: 600,
      color: '#1f2937',
      marginBottom: '12px',
      margin: 0,
    }),
    []
  );

  const descriptionStyle = useMemo<CSSProperties>(
    () => ({
      fontSize: '14px',
      color: '#6b7280',
      marginBottom: '24px',
      maxWidth: '400px',
      lineHeight: 1.6,
    }),
    []
  );

  const pathnameStyle = useMemo<CSSProperties>(
    () => ({
      fontSize: '12px',
      color: '#9ca3af',
      fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
      padding: '8px 16px',
      backgroundColor: '#f3f4f6',
      borderRadius: '6px',
      marginBottom: '16px',
    }),
    []
  );

  const badgeStyle = useMemo<CSSProperties>(
    () => ({
      display: 'inline-block',
      padding: '6px 16px',
      backgroundColor: primaryColor,
      color: '#ffffff',
      borderRadius: '16px',
      fontSize: '12px',
      fontWeight: 500,
      letterSpacing: '0.025em',
    }),
    [primaryColor]
  );

  return {
    containerStyle,
    iconStyle,
    titleStyle,
    descriptionStyle,
    pathnameStyle,
    badgeStyle,
  };
}

// ============================================================================
// Component
// ============================================================================

/**
 * Placeholder Page Component
 *
 * <p>Displays a friendly placeholder for features under development.
 * Provides consistent styling and branding across the platform.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * <PlaceholderPage
 *   title="User Management"
 *   description="This feature is coming soon..."
 *   pathname="/admin/users"
 *   branding={{ primaryColor: '#3b82f6' }}
 * />
 * }</pre>
 *
 * @param props - Component props
 * @returns React node
 *
 * @since 3.2.0
 */
export function PlaceholderPage({
  title = 'Coming Soon',
  description = 'This feature is under development. Please check back later.',
  icon = '🚧',
  pathname,
  statusText = 'In Development',
  branding,
  className,
  style,
}: PlaceholderPageProps): ReactNode {
  const primaryColor = branding?.primaryColor || '#3b82f6';

  const styles = useStyles(primaryColor, branding?.backgroundColor);

  return (
    <div
      className={className}
      style={{ ...styles.containerStyle, ...style }}
      role="main"
      aria-label="Placeholder page"
    >
      {/* Icon */}
      <div style={styles.iconStyle} aria-hidden="true">
        {icon}
      </div>

      {/* Title */}
      <h1 style={styles.titleStyle}>{title}</h1>

      {/* Description */}
      <p style={styles.descriptionStyle}>{description}</p>

      {/* Pathname (debug info) */}
      {pathname && <code style={styles.pathnameStyle}>{pathname}</code>}

      {/* Status Badge */}
      <span style={styles.badgeStyle}>{statusText}</span>
    </div>
  );
}

// ============================================================================
// Factory Functions
// ============================================================================

/**
 * Create Placeholder Page Factory
 *
 * <p>Creates a configured placeholder page component with default values.
 * Useful for creating multiple placeholders with consistent styling.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * const MyPlaceholder = createSimplePlaceholderPage({
 *   defaultTitle: 'Feature Coming Soon',
 *   branding: { primaryColor: '#10b981' },
 * });
 *
 * // Usage: <MyPlaceholder title="Reports" pathname="/reports" />
 * }</pre>
 *
 * @param config - Factory configuration
 * @returns Configured placeholder page component
 *
 * @since 3.2.0
 */
export function createSimplePlaceholderPage(
  config: SimplePlaceholderConfig = {}
): React.FC<Partial<PlaceholderPageProps>> {
  const {
    defaultTitle,
    defaultDescription,
    defaultIcon,
    defaultStatusText,
    branding: defaultBranding,
  } = config;

  return function ConfiguredPlaceholderPage(props: Partial<PlaceholderPageProps>) {
    return (
      <PlaceholderPage
        title={props.title ?? defaultTitle}
        description={props.description ?? defaultDescription}
        icon={props.icon ?? defaultIcon}
        statusText={props.statusText ?? defaultStatusText}
        branding={{ ...defaultBranding, ...props.branding }}
        pathname={props.pathname}
        className={props.className}
        style={props.style}
      />
    );
  };
}

export default PlaceholderPage;
