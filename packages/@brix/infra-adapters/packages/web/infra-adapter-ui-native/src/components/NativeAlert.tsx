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
 * @file Native Alert Component
 * @description Pure CSS implementation of AlertProps from UIAdapter contract.
 *              Feedback component for important messages to the user.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeAlert
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Four severity levels: success, info, warning, error
 * - Optional title, description, icon, and close button
 * - Banner mode for full-width display
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic feedback component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for user notifications.
 * Replaces direct MUI Alert usage in enterprise-solutions plugins.
 */

import type { FC, CSSProperties } from 'react';
import { useState } from 'react';
import type { AlertProps, AlertSeverity } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Severity Configurations
// ============================================================================

/**
 * Severity Color Palettes
 *
 * <p>Background, text, and icon colors for each severity level.</p>
 */
interface SeverityColors {
  background: string;
  color: string;
  borderColor: string;
  iconColor: string;
}

const SEVERITY_COLORS: Record<AlertSeverity, SeverityColors> = {
  success: {
    background: '#edf7ed',
    color: '#1e4620',
    borderColor: '#c3e6cb',
    iconColor: '#2e7d32',
  },
  info: {
    background: '#e5f6fd',
    color: '#014361',
    borderColor: '#b8daff',
    iconColor: '#0288d1',
  },
  warning: {
    background: '#fff4e5',
    color: '#663c00',
    borderColor: '#ffe69c',
    iconColor: '#ed6c02',
  },
  error: {
    background: '#fdeded',
    color: '#5f2120',
    borderColor: '#f5c2c7',
    iconColor: '#d32f2f',
  },
};

/**
 * Default Icons for Severities
 *
 * <p>Unicode icons representing each severity level.</p>
 */
const SEVERITY_ICONS: Record<AlertSeverity, string> = {
  success: '✓',
  info: 'ℹ',
  warning: '⚠',
  error: '✕',
};

// ============================================================================
// Alert Component
// ============================================================================

/**
 * Native Alert Component
 *
 * <p>Pure CSS implementation of AlertProps from UIAdapter contract.
 * Displays an important message or notification to the user.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS</li>
 *   <li>Four severity levels: success, info, warning, error</li>
 *   <li>Optional title and description</li>
 *   <li>Custom or default icon</li>
 *   <li>Closable with callback</li>
 *   <li>Optional action button</li>
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
 * const { Alert } = useUI();
 *
 * <Alert
 *   severity="success"
 *   title="Operation Successful"
 *   description="Your changes have been saved."
 *   closable
 *   onClose={() => setVisible(false)}
 * />
 *
 * <Alert
 *   severity="error"
 *   description="Failed to load data. Please try again."
 *   action={<Button size="small">Retry</Button>}
 * />
 * ```
 *
 * @param props - AlertProps from UIAdapter contract
 * @returns Native Alert component
 */
export const NativeAlert: FC<AlertProps> = ({
  severity = 'info',
  title,
  description,
  icon,
  showIcon = true,
  closable = false,
  action,
  banner = false,
  onClose,
  style,
  className,
  'data-testid': dataTestId,
}) => {
  // Internal visibility state (for closable alerts)
  const [visible, setVisible] = useState(true);

  // If alert is closed, don't render
  if (!visible) return null;

  // Get colors for current severity
  const colors = SEVERITY_COLORS[severity];

  // Handle close
  const handleClose = () => {
    setVisible(false);
    onClose?.();
  };

  // Container styles
  const containerStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'flex-start',
    padding: '12px 16px',
    borderRadius: banner ? 0 : 4,
    backgroundColor: colors.background,
    color: colors.color,
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    fontSize: '14px',
    lineHeight: 1.5,
    border: banner ? 'none' : `1px solid ${colors.borderColor}`,
    position: banner ? 'relative' : undefined,
    width: banner ? '100%' : undefined,
    boxSizing: 'border-box',
    ...style,
  };

  // Icon styles
  const iconStyle: CSSProperties = {
    flexShrink: 0,
    marginRight: 12,
    marginTop: 2,
    fontSize: '18px',
    color: colors.iconColor,
    fontWeight: 'bold',
  };

  // Content wrapper styles
  const contentStyle: CSSProperties = {
    flex: 1,
    minWidth: 0,
  };

  // Title styles
  const titleStyle: CSSProperties = {
    fontWeight: 500,
    fontSize: '14px',
    marginBottom: description ? 4 : 0,
    color: 'inherit',
  };

  // Description styles
  const descriptionStyle: CSSProperties = {
    fontSize: '14px',
    color: 'inherit',
    opacity: 0.9,
  };

  // Action area styles
  const actionStyle: CSSProperties = {
    marginLeft: 12,
    flexShrink: 0,
  };

  // Close button styles
  const closeButtonStyle: CSSProperties = {
    marginLeft: 12,
    flexShrink: 0,
    background: 'none',
    border: 'none',
    padding: 4,
    cursor: 'pointer',
    fontSize: '16px',
    color: 'inherit',
    opacity: 0.6,
    borderRadius: 4,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    transition: 'opacity 0.2s, background-color 0.2s',
  };

  return (
    <div
      role="alert"
      style={containerStyle}
      className={className}
      data-testid={dataTestId}
      data-severity={severity}
    >
      {/* Icon */}
      {showIcon && (
        <span style={iconStyle}>
          {icon || SEVERITY_ICONS[severity]}
        </span>
      )}

      {/* Content */}
      <div style={contentStyle}>
        {title && <div style={titleStyle}>{title}</div>}
        {description && <div style={descriptionStyle}>{description}</div>}
      </div>

      {/* Action */}
      {action && <div style={actionStyle}>{action}</div>}

      {/* Close button */}
      {closable && (
        <button
          type="button"
          onClick={handleClose}
          style={closeButtonStyle}
          aria-label="Close alert"
          onMouseEnter={(e) => {
            e.currentTarget.style.opacity = '1';
            e.currentTarget.style.backgroundColor = 'rgba(0, 0, 0, 0.04)';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.opacity = '0.6';
            e.currentTarget.style.backgroundColor = 'transparent';
          }}
        >
          ✕
        </button>
      )}
    </div>
  );
};

NativeAlert.displayName = 'NativeAlert';

export default NativeAlert;
