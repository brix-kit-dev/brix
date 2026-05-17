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
 * @file Native Form Component
 * @description Pure CSS implementation of FormProps and FormItemProps from UIAdapter contract.
 *              Form container and field wrapper components for form layouts.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeForm
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Support for horizontal, vertical, and inline layouts
 * - Customizable label alignment and width
 * - Validation status styling
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic form component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for form organization.
 * Replaces direct MUI Form/FormControl usage in enterprise-solutions plugins.
 */

import type { FC, CSSProperties } from 'react';
import type { FormProps, FormItemProps, FormLayout, ValidateStatus, FormComponentType } from '@brix-sdk/runtime-sdk-api-web';
import { useForm as useFormImpl } from '@brix-sdk/runtime-sdk-react';

// ============================================================================
// Status Colors
// ============================================================================

/**
 * Validation Status Colors
 *
 * <p>Colors for different validation states.</p>
 */
const STATUS_COLORS: Record<ValidateStatus, string> = {
  success: '#2e7d32',
  warning: '#ed6c02',
  error: '#d32f2f',
  validating: '#1976d2',
};

// ============================================================================
// Form Component
// ============================================================================

/**
 * Native Form Component
 *
 * <p>Pure CSS implementation of FormProps from UIAdapter contract.
 * Provides a container for form fields with layout control.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS</li>
 *   <li>Three layout modes: horizontal, vertical, inline</li>
 *   <li>Configurable label alignment and width</li>
 *   <li>Size variants</li>
 *   <li>Form submission handling</li>
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
 * const { Form, FormItem, Input, Button } = useUI();
 *
 * <Form layout="horizontal" labelWidth={120} onSubmit={handleSubmit}>
 *   <FormItem label="Name" required>
 *     <Input value={name} onChange={(e) => setName(e.target.value)} />
 *   </FormItem>
 *   <FormItem label="Email">
 *     <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
 *   </FormItem>
 *   <FormItem>
 *     <Button type="submit" variant="primary">Submit</Button>
 *   </FormItem>
 * </Form>
 * ```
 *
 * @param props - FormProps from UIAdapter contract
 * @returns Native Form component
 */
const NativeFormComponent: FC<FormProps> = ({
  layout = 'vertical',
  labelWidth,
  labelAlign = 'right',
  size = 'medium',
  disabled = false,
  requiredMark = true,
  colon = true,
  onSubmit,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Handle form submission
  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    onSubmit?.(e);
  };

  // Form container styles
  const formStyle: CSSProperties = {
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    fontSize: size === 'small' ? '13px' : size === 'large' ? '15px' : '14px',
    display: layout === 'inline' ? 'flex' : 'block',
    flexWrap: layout === 'inline' ? 'wrap' : undefined,
    gap: layout === 'inline' ? '16px' : undefined,
    opacity: disabled ? 0.6 : 1,
    pointerEvents: disabled ? 'none' : undefined,
    ...style,
  };

  return (
    <form
      style={formStyle}
      className={className}
      onSubmit={handleSubmit}
      data-testid={dataTestId}
      data-form-layout={layout}
      data-label-width={labelWidth}
      data-label-align={labelAlign}
      data-required-mark={requiredMark}
      data-colon={colon}
    >
      {children}
    </form>
  );
};

NativeFormComponent.displayName = 'NativeForm';

// ============================================================================
// Compound export per FormComponentType contract (Stability Reform v1.0 C-8).
// `useForm` is attached below; the static type is widened here so consumers of
// `UIAdapter.Form` see the canonical compound contract at compile time.
// ============================================================================
export const NativeForm = NativeFormComponent as unknown as FormComponentType<FormProps>;

// ============================================================================
// FormItem Component
// ============================================================================

/**
 * Native FormItem Component
 *
 * <p>Pure CSS implementation of FormItemProps from UIAdapter contract.
 * Wrapper for form fields with label and validation display.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS</li>
 *   <li>Label with required indicator</li>
 *   <li>Validation status styling</li>
 *   <li>Helper text and extra content slots</li>
 *   <li>Feedback icons for validation states</li>
 * </ul>
 *
 * @example
 * ```tsx
 * const { FormItem, Input } = useUI();
 *
 * <FormItem
 *   label="Email"
 *   required
 *   validateStatus="error"
 *   helperText="Please enter a valid email"
 * >
 *   <Input type="email" />
 * </FormItem>
 * ```
 *
 * @param props - FormItemProps from UIAdapter contract
 * @returns Native FormItem component
 */
export const NativeFormItem: FC<FormItemProps> = ({
  label,
  labelWidth,
  required = false,
  validateStatus,
  hasFeedback = false,
  helperText,
  extra,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Determine status color
  const statusColor = validateStatus ? STATUS_COLORS[validateStatus] : undefined;

  // Container styles (supports both vertical and horizontal layouts)
  const containerStyle: CSSProperties = {
    marginBottom: 24,
    ...style,
  };

  // Label wrapper styles
  const labelWrapperStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    marginBottom: 8,
  };

  // Label styles
  const labelStyle: CSSProperties = {
    fontSize: '14px',
    color: 'rgba(0, 0, 0, 0.87)',
    fontWeight: 400,
    width: labelWidth,
    flexShrink: labelWidth ? 0 : undefined,
  };

  // Required asterisk styles
  const requiredStyle: CSSProperties = {
    color: '#d32f2f',
    marginRight: 4,
  };

  // Control wrapper styles
  const controlWrapperStyle: CSSProperties = {
    position: 'relative',
    flex: 1,
  };

  // Helper text styles
  const helperTextStyle: CSSProperties = {
    marginTop: 4,
    fontSize: '12px',
    color: statusColor || 'rgba(0, 0, 0, 0.6)',
    lineHeight: 1.66,
  };

  // Extra content styles
  const extraStyle: CSSProperties = {
    marginTop: 4,
    fontSize: '12px',
    color: 'rgba(0, 0, 0, 0.45)',
    lineHeight: 1.66,
  };

  // Feedback icon
  const renderFeedbackIcon = () => {
    if (!hasFeedback || !validateStatus) return null;

    const iconStyle: CSSProperties = {
      position: 'absolute',
      right: 8,
      top: '50%',
      transform: 'translateY(-50%)',
      fontSize: '16px',
      color: statusColor,
    };

    switch (validateStatus) {
      case 'success':
        return <span style={iconStyle}>✓</span>;
      case 'error':
        return <span style={iconStyle}>✗</span>;
      case 'warning':
        return <span style={iconStyle}>⚠</span>;
      case 'validating':
        return (
          <span
            style={{
              ...iconStyle,
              animation: 'spin 1s linear infinite',
            }}
          >
            ⟳
          </span>
        );
      default:
        return null;
    }
  };

  return (
    <div style={containerStyle} className={className} data-testid={dataTestId}>
      {/* Label */}
      {label && (
        <div style={labelWrapperStyle}>
          <label style={labelStyle}>
            {required && <span style={requiredStyle}>*</span>}
            {label}
          </label>
        </div>
      )}

      {/* Control wrapper */}
      <div style={controlWrapperStyle}>
        {children}
        {hasFeedback && renderFeedbackIcon()}
      </div>

      {/* Helper text */}
      {helperText && <div style={helperTextStyle}>{helperText}</div>}

      {/* Extra content */}
      {extra && <div style={extraStyle}>{extra}</div>}

      {/* Validation styles */}
      {validateStatus && (
        <style>{`
          @keyframes spin { to { transform: translateY(-50%) rotate(360deg); } }
        `}</style>
      )}
    </div>
  );
};

NativeFormItem.displayName = 'NativeFormItem';

export default NativeForm;


// ============================================================================
// Stability Reform v1.0 -- C-8 (Form-state convergence)
// ----------------------------------------------------------------------------
// Attach the shared useForm hook so callers can use the compound-component
// pattern: useUI().Form.useForm<T>(opts). The implementation lives in
// @brix-sdk/runtime-sdk-react to guarantee identical behaviour across every
// adapter and avoid forking form-state semantics.
// ============================================================================
NativeForm.useForm = useFormImpl as FormComponentType<FormProps>['useForm'];
