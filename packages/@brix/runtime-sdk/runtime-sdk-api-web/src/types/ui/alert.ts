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
 * @file Alert Component Type Definitions
 * @description Defines types for the Alert feedback component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/alert
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Alert provides static notification banners for contextual feedback
 * - Supports multiple severity levels with semantic colors
 * - Plugins must obtain Alert through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties } from 'react';

/**
 * Alert Severity Levels
 *
 * Defines the semantic meaning and visual style of the alert.
 * - success: Positive outcome, operation completed
 * - info: Neutral information, no action required
 * - warning: Caution needed, potential issues
 * - error: Problem occurred, action may be required
 */
export type AlertSeverity = 'success' | 'info' | 'warning' | 'error';

/**
 * Alert Variant
 *
 * Visual style variants for alerts.
 * - filled: Solid background color
 * - outlined: Border with transparent background
 * - standard: Light background tint (default)
 */
export type AlertVariant = 'filled' | 'outlined' | 'standard';

/**
 * Alert Component Props
 *
 * Static notification component for displaying contextual messages.
 * Used for form validation summaries, system status, and user feedback.
 *
 * **Design Principle: Contextual Feedback**
 * Alerts provide persistent, in-context feedback that remains visible
 * until dismissed or the condition changes. Use for important information
 * that users need to acknowledge.
 *
 * @example
 * ```tsx
 * const { Alert, Stack } = useUI();
 *
 * // Basic alerts by severity
 * <Stack spacing={16}>
 *   <Alert severity="success">Operation completed successfully!</Alert>
 *   <Alert severity="info">This feature is in beta.</Alert>
 *   <Alert severity="warning">Your session will expire in 5 minutes.</Alert>
 *   <Alert severity="error">Failed to save changes. Please try again.</Alert>
 * </Stack>
 *
 * // Alert with title and description
 * <Alert
 *   severity="error"
 *   title="Validation Error"
 * >
 *   Please fix the following issues before submitting:
 *   <ul>
 *     <li>Email format is invalid</li>
 *     <li>Password must be at least 8 characters</li>
 *   </ul>
 * </Alert>
 *
 * // Closable alert
 * <Alert
 *   severity="info"
 *   closable
 *   onClose={() => setShowTip(false)}
 * >
 *   Pro tip: You can drag and drop files to upload.
 * </Alert>
 *
 * // Alert with action button
 * <Alert
 *   severity="warning"
 *   action={
 *     <Button size="small" variant="text" onClick={handleUpgrade}>
 *       Upgrade Now
 *     </Button>
 *   }
 * >
 *   Your plan is expiring soon.
 * </Alert>
 * ```
 */
export interface AlertProps {
  /**
   * Alert Severity
   *
   * Determines the semantic meaning and color scheme.
   * @default 'info'
   */
  severity?: AlertSeverity;

  /**
   * Alert Variant
   *
   * Visual style of the alert.
   * @default 'standard'
   */
  variant?: AlertVariant;

  /**
   * Alert Title
   *
   * Optional title displayed above the message.
   * Use for multi-line alerts or when additional context is needed.
   */
  title?: ReactNode;

  /**
   * Custom Icon
   *
   * Icon name to override the default severity icon.
   * Set to false to hide the icon entirely.
   */
  icon?: string | false;

  /**
   * Closable Mode
   *
   * When true, displays a close button.
   * @default false
   */
  closable?: boolean;

  /**
   * Close Handler
   *
   * Callback fired when the close button is clicked.
   * Only triggered when closable is true.
   */
  onClose?: () => void;

  /**
   * Action Element
   *
   * Action buttons or links displayed at the end of the alert.
   */
  action?: ReactNode;

  /**
   * Show Icon
   *
   * When true, displays the severity icon.
   * @default true
   */
  showIcon?: boolean;

  /**
   * Banner Mode
   *
   * When true, renders as a full-width banner without border-radius.
   * Useful for page-level notifications.
   *
   * @default false
   */
  banner?: boolean;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the alert container.
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

  /**
   * Alert Message
   *
   * The main content/message of the alert.
   */
  children?: ReactNode;
}
