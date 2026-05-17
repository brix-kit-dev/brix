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
 * @file MUI Form Component
 * @description Material UI implementation of FormProps and FormItemProps from UIAdapter contract.
 *              Form container and field wrapper with layout and validation display.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiForm
 * @version 3.2.0
 *
 * [Design Principles]
 * - Form provides layout structure, not validation logic
 * - Validation handled by external libs (React Hook Form, Formik)
 * - FormItem displays label, field, and validation feedback
 * - Three layout modes: horizontal, vertical, inline
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic form component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for form structure.
 * Custom implementation as MUI lacks native Form/FormItem pattern.
 */

import type { FC, FormEvent, ReactNode } from 'react';
import { createContext, useContext } from 'react';
import type {
  FormProps,
  FormItemProps,
  FormLayout,
  ValidateStatus,
  ComponentSize,
  FormComponentType,
} from '@brix-sdk/runtime-sdk-api-web';
import { useForm as useFormImpl } from '@brix-sdk/runtime-sdk-react';
import Box from '@mui/material/Box';
import FormControl from '@mui/material/FormControl';
import FormLabel from '@mui/material/FormLabel';
import FormHelperText from '@mui/material/FormHelperText';
import CircularProgress from '@mui/material/CircularProgress';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import WarningIcon from '@mui/icons-material/Warning';

// ============================================================================
// Form Context
// ============================================================================

/**
 * Form Context Interface
 *
 * Shared form configuration passed to FormItem children.
 */
interface FormContextValue {
  layout: FormLayout;
  labelWidth?: number | string;
  labelAlign?: 'left' | 'right';
  size: ComponentSize;
  disabled: boolean;
  requiredMark: boolean;
  colon: boolean;
}

/**
 * Form Context
 *
 * Provides form-level configuration to FormItem children.
 */
const FormContext = createContext<FormContextValue>({
  layout: 'vertical',
  size: 'medium',
  disabled: false,
  requiredMark: true,
  colon: false,
});

// ============================================================================
// MuiForm Component Implementation
// ============================================================================

/**
 * MUI Form Component
 *
 * <p>Material UI implementation of FormProps from UIAdapter contract.
 * Provides layout structure for form fields with consistent styling.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Three layout modes: horizontal, vertical, inline</li>
 *   <li>Context-based configuration for child FormItems</li>
 *   <li>Configurable label width and alignment</li>
 *   <li>Form-level disabled state</li>
 *   <li>Required mark and colon options</li>
 * </ul>
 *
 * <h3>Architectural Constraints:</h3>
 * <ul>
 *   <li>This component is an atomic building block</li>
 *   <li>Shell layer uses this via UIAdapter interface</li>
 *   <li>No direct import allowed in Plugin layer</li>
 *   <li>Validation logic handled externally</li>
 * </ul>
 *
 * @example
 * ```tsx
 * const { Form, FormItem, Input, Button } = useUI();
 *
 * <Form layout="vertical" onSubmit={handleSubmit}>
 *   <FormItem label="Username" required>
 *     <Input name="username" value={data.username} onChange={handleChange} />
 *   </FormItem>
 *   <FormItem label="Email" required>
 *     <Input type="email" name="email" value={data.email} onChange={handleChange} />
 *   </FormItem>
 *   <FormItem>
 *     <Button type="submit" variant="primary">Submit</Button>
 *   </FormItem>
 * </Form>
 * ```
 *
 * @param props - FormProps from UIAdapter contract
 * @returns Form element with context provider
 */
const MuiFormComponent: FC<FormProps> = ({
  layout = 'vertical',
  labelWidth,
  labelAlign = 'right',
  size = 'medium',
  disabled = false,
  requiredMark = true,
  colon = false,
  onSubmit,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Handle form submission
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    onSubmit?.(event);
  };

  // Context value for FormItem children
  const contextValue: FormContextValue = {
    layout,
    labelWidth,
    labelAlign,
    size,
    disabled,
    requiredMark,
    colon,
  };

  // Layout-specific styles
  const formStyles = {
    display: layout === 'inline' ? 'flex' : 'block',
    flexWrap: layout === 'inline' ? 'wrap' : undefined,
    gap: layout === 'inline' ? 16 : undefined,
    alignItems: layout === 'inline' ? 'flex-start' : undefined,
    ...style,
  };

  return (
    <FormContext.Provider value={contextValue}>
      <Box
        component="form"
        onSubmit={handleSubmit}
        sx={formStyles}
        className={className}
        data-testid={dataTestId}
      >
        {children}
      </Box>
    </FormContext.Provider>
  );
};

// ============================================================================
// Compound export per FormComponentType contract (Stability Reform v1.0 C-8).
// `useForm` is attached below; the static type is widened here so consumers of
// `UIAdapter.Form` see the canonical compound contract at compile time.
// ============================================================================
export const MuiForm = MuiFormComponent as unknown as FormComponentType<FormProps>;

// ============================================================================
// Validation Status Icons
// ============================================================================

/**
 * Validation status icons mapping
 */
const VALIDATION_ICONS: Record<ValidateStatus, ReactNode> = {
  success: <CheckCircleIcon fontSize="small" color="success" />,
  warning: <WarningIcon fontSize="small" color="warning" />,
  error: <ErrorIcon fontSize="small" color="error" />,
  validating: <CircularProgress size={16} />,
};

/**
 * Validation status colors mapping
 */
const VALIDATION_COLORS: Record<ValidateStatus, string> = {
  success: 'success.main',
  warning: 'warning.main',
  error: 'error.main',
  validating: 'primary.main',
};

// ============================================================================
// MuiFormItem Component Implementation
// ============================================================================

/**
 * MUI FormItem Component
 *
 * <p>Material UI implementation of FormItemProps from UIAdapter contract.
 * Wrapper for form fields providing label and validation feedback.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Label with required indicator</li>
 *   <li>Validation status icons and colors</li>
 *   <li>Helper text for errors/guidance</li>
 *   <li>Extra information slot</li>
 *   <li>Layout inherited from parent Form</li>
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
 * const { FormItem, Input } = useUI();
 *
 * <FormItem
 *   label="Email"
 *   required
 *   validateStatus="error"
 *   helperText="Please enter a valid email address"
 * >
 *   <Input type="email" value={email} onChange={setEmail} error />
 * </FormItem>
 * ```
 *
 * @param props - FormItemProps from UIAdapter contract
 * @returns FormControl with label and validation display
 */
export const MuiFormItem: FC<FormItemProps> = ({
  label,
  labelWidth: itemLabelWidth,
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
  // Get form context
  const formContext = useContext(FormContext);
  const {
    layout,
    labelWidth: formLabelWidth,
    labelAlign,
    requiredMark,
    colon,
    disabled,
  } = formContext;

  // Determine effective label width
  const effectiveLabelWidth = itemLabelWidth ?? formLabelWidth;

  // Determine if error state for MUI FormControl
  const hasError = validateStatus === 'error';

  // Build layout styles based on form layout
  const isHorizontal = layout === 'horizontal';
  const containerStyles = {
    display: isHorizontal ? 'flex' : 'block',
    alignItems: isHorizontal ? 'flex-start' : undefined,
    marginBottom: layout !== 'inline' ? 24 : 0,
    ...style,
  };

  const labelStyles = {
    width: isHorizontal ? effectiveLabelWidth : undefined,
    minWidth: isHorizontal ? effectiveLabelWidth : undefined,
    textAlign: isHorizontal ? labelAlign : undefined,
    paddingRight: isHorizontal ? 8 : 0,
    paddingTop: isHorizontal ? 8 : 0,
    marginBottom: !isHorizontal && label ? 4 : 0,
  };

  const controlStyles = {
    flex: isHorizontal ? 1 : undefined,
  };

  return (
    <Box sx={containerStyles} className={className} data-testid={dataTestId}>
      {/* Label */}
      {label && (
        <FormLabel
          required={required && requiredMark}
          disabled={disabled}
          sx={labelStyles}
        >
          {label}
          {colon && ':'}
        </FormLabel>
      )}

      {/* Control container */}
      <Box sx={controlStyles}>
        <FormControl
          fullWidth
          error={hasError}
          disabled={disabled}
        >
          {/* Field content */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            {children}
            {/* Feedback icon */}
            {hasFeedback && validateStatus && VALIDATION_ICONS[validateStatus]}
          </Box>

          {/* Helper text */}
          {helperText && (
            <FormHelperText
              sx={{
                color: validateStatus ? VALIDATION_COLORS[validateStatus] : undefined,
              }}
            >
              {helperText}
            </FormHelperText>
          )}

          {/* Extra information */}
          {extra && (
            <FormHelperText sx={{ color: 'text.secondary' }}>
              {extra}
            </FormHelperText>
          )}
        </FormControl>
      </Box>
    </Box>
  );
};

export default MuiForm;

// ============================================================================
// Stability Reform v1.0 -- C-8 (Form-state convergence)
// ----------------------------------------------------------------------------
// Attach the shared useForm hook so callers can use the compound-component
// pattern: useUI().Form.useForm<T>(opts). The implementation lives in
// @brix-sdk/runtime-sdk-react to guarantee identical behaviour across
// every adapter and avoid forking form-state semantics.
//
// The `useForm` static is typed via FormComponentType so consumers obtain the
// canonical compound contract.
// ============================================================================
MuiForm.useForm = useFormImpl as FormComponentType<FormProps>['useForm'];
