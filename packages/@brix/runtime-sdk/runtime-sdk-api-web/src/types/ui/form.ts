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
 * @file Form Component Type Definitions
 * @description Defines types for the Form and FormItem components in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/form
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Form provides container for form fields with layout control
 * - FormItem wraps individual fields with label and validation display
 * - Plugins must obtain Form/FormItem through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 *
 * [Design Philosophy]
 * The Form component provides layout and structure, not validation logic.
 * Validation should be handled externally (React Hook Form, Formik, etc.)
 * and error states passed to FormItem via props.
 */

import type { ReactNode, CSSProperties, FormEvent } from 'react';
import type { ComponentSize } from './common';

/**
 * Form Layout Mode
 *
 * Determines the arrangement of labels relative to form fields.
 * - horizontal: Labels beside fields (default for wide screens)
 * - vertical: Labels above fields (stack layout)
 * - inline: All fields in a single row
 */
export type FormLayout = 'horizontal' | 'vertical' | 'inline';

/**
 * Label Alignment
 *
 * Text alignment for labels in horizontal layout.
 */
export type LabelAlign = 'left' | 'right';

/**
 * Form Component Props
 *
 * Container component for form fields providing consistent layout
 * and styling across form elements.
 *
 * **Design Principle: Layout Not Logic**
 * Form provides structure and visual consistency. Validation logic
 * should be handled by external libraries (React Hook Form, Formik)
 * with error states passed through FormItem props.
 *
 * @example
 * ```tsx
 * const { Form, FormItem, Input, Button, Select } = useUI();
 *
 * // Basic vertical form
 * <Form layout="vertical" onSubmit={handleSubmit}>
 *   <FormItem label="Username" required>
 *     <Input name="username" value={data.username} onChange={handleChange} />
 *   </FormItem>
 *   <FormItem label="Email" required error={!!errors.email} helperText={errors.email}>
 *     <Input type="email" name="email" value={data.email} onChange={handleChange} />
 *   </FormItem>
 *   <FormItem>
 *     <Button type="submit" variant="primary">Submit</Button>
 *   </FormItem>
 * </Form>
 *
 * // Horizontal form with label width
 * <Form layout="horizontal" labelWidth={120}>
 *   <FormItem label="Category">
 *     <Select
 *       options={categories}
 *       value={data.category}
 *       onChange={(v) => setData({ ...data, category: v })}
 *     />
 *   </FormItem>
 * </Form>
 *
 * // Inline form for search/filters
 * <Form layout="inline" onSubmit={handleSearch}>
 *   <FormItem>
 *     <Input placeholder="Search..." value={query} onChange={setQuery} />
 *   </FormItem>
 *   <FormItem>
 *     <Button type="submit">Search</Button>
 *   </FormItem>
 * </Form>
 * ```
 */
export interface FormProps {
  /**
   * Form Layout
   *
   * Determines label and field arrangement.
   * @default 'vertical'
   */
  layout?: FormLayout;

  /**
   * Label Width
   *
   * Fixed width for labels in horizontal layout.
   * Can be number (pixels) or string (CSS value).
   */
  labelWidth?: number | string;

  /**
   * Label Alignment
   *
   * Text alignment for labels in horizontal layout.
   * @default 'right'
   */
  labelAlign?: LabelAlign;

  /**
   * Form Size
   *
   * Default size for all form controls.
   * Can be overridden on individual FormItem.
   *
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Disabled State
   *
   * When true, all form fields are disabled.
   * @default false
   */
  disabled?: boolean;

  /**
   * Required Mark
   *
   * Show/hide required asterisk marks.
   * @default true
   */
  requiredMark?: boolean;

  /**
   * Colon After Label
   *
   * When true, adds colon after labels.
   * @default false
   */
  colon?: boolean;

  /**
   * Submit Handler
   *
   * Callback fired on form submission.
   * Receives the form event for preventDefault and data extraction.
   *
   * @param event - The form submit event
   */
  onSubmit?: (event: FormEvent<HTMLFormElement>) => void;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the form element.
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
   * Form Content
   *
   * FormItem components containing form fields.
   */
  children?: ReactNode;
}

/**
 * Validation Status
 *
 * Visual validation state for form items.
 */
export type ValidateStatus = 'success' | 'warning' | 'error' | 'validating';

/**
 * FormItem Component Props
 *
 * Wrapper for individual form fields providing label, validation feedback,
 * and helper text display.
 *
 * @example
 * ```tsx
 * const { FormItem, Input } = useUI();
 *
 * // Basic field with label
 * <FormItem label="Name" required>
 *   <Input value={name} onChange={setName} />
 * </FormItem>
 *
 * // Field with validation error
 * <FormItem
 *   label="Email"
 *   required
 *   validateStatus="error"
 *   helperText="Please enter a valid email address"
 * >
 *   <Input type="email" value={email} onChange={setEmail} error />
 * </FormItem>
 *
 * // Field with extra information
 * <FormItem
 *   label="Password"
 *   required
 *   extra="Password must be at least 8 characters"
 * >
 *   <Input type="password" value={password} onChange={setPassword} />
 * </FormItem>
 * ```
 */
export interface FormItemProps {
  /**
   * Field Label
   *
   * Label text displayed for the form field.
   * Can be string or ReactNode for custom formatting.
   */
  label?: ReactNode;

  /**
   * Label Width
   *
   * Override the form-level label width for this item.
   */
  labelWidth?: number | string;

  /**
   * Required Field
   *
   * When true, displays a required asterisk indicator.
   * This is visual only; actual validation must be handled separately.
   *
   * @default false
   */
  required?: boolean;

  /**
   * Validation Status
   *
   * Visual indicator of validation state.
   * Controls the color and icon displayed with the field.
   */
  validateStatus?: ValidateStatus;

  /**
   * Has Feedback
   *
   * When true, shows a validation icon based on validateStatus.
   * @default false
   */
  hasFeedback?: boolean;

  /**
   * Helper Text
   *
   * Text displayed below the field.
   * Used for validation errors or field guidance.
   * Color changes based on validateStatus.
   */
  helperText?: ReactNode;

  /**
   * Extra Information
   *
   * Additional text/content displayed below helper text.
   * Always displayed in neutral color regardless of validation state.
   */
  extra?: ReactNode;

  /**
   * Tooltip
   *
   * Tooltip content displayed next to the label.
   * Use for detailed field explanations.
   */
  tooltip?: ReactNode;

  /**
   * No Style Mode
   *
   * When true, renders children without wrapper styling.
   * Useful for nested FormItems or custom layouts.
   *
   * @default false
   */
  noStyle?: boolean;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the form item container.
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
   * Form Control
   *
   * The form field component (Input, Select, etc.).
   */
  children?: ReactNode;
}

// ============================================================================
// Form Instance Contract (Frontend Stability Reform Plan v1.0 — C-8)
// ----------------------------------------------------------------------------
// Plan §6.2 mandates that the platform converge on a single form-state API:
// `useUI().Form.useForm()`.  Historically each plugin owned its own
// `useState + onChange` pair per field, producing inconsistent validation,
// stale closures, and double-submit bugs.
//
// This block defines the cross-adapter contract for the form instance
// returned by `useForm()`.  The implementation lives in
// `runtime-sdk-react/src/hooks/useForm.ts` and is exposed on the Form
// component as a static method (compound component pattern), so plugins call
// `Form.useForm()` exactly as the plan requires while the actual state
// machine remains UI-library-neutral.
// ============================================================================

/**
 * Field Path
 *
 * Identifier for a form field. Strict string keys keep the type system
 * informative — no nested-path dot notation is supported by design (one
 * field = one key), which matches the FormItem `name` prop and prevents
 * accidental coupling to the underlying object shape.
 */
export type FormFieldPath<TValues> = keyof TValues & string;

/**
 * Field Validation Result
 *
 * `null` / `undefined` → valid. A string → human-readable error message.
 * Validators MUST be pure synchronous functions (or return a Promise that
 * resolves with the same shape) so that `validateField` can be invoked
 * during `onChange` without blocking the UI thread.
 */
export type FieldValidationResult = string | null | undefined;

/**
 * Field Validator
 *
 * Pure function evaluating a single field. Receives the field value and
 * the full set of values (for cross-field validation, e.g. password
 * confirmation). Returning a Promise is allowed for async checks
 * (uniqueness lookups via HttpCapability), but synchronous validators are
 * preferred — async validators are awaited only inside `validate()`.
 */
export type FieldValidator<TValues, TKey extends FormFieldPath<TValues> = FormFieldPath<TValues>> =
  (value: TValues[TKey], allValues: TValues) =>
    | FieldValidationResult
    | Promise<FieldValidationResult>;

/**
 * Form Validators
 *
 * Optional per-field validator map passed to `useForm({ validators })`.
 * Fields without a validator entry are always considered valid.
 */
export type FormValidators<TValues> = {
  readonly [K in FormFieldPath<TValues>]?: FieldValidator<TValues, K>;
};

/**
 * Form Errors
 *
 * Per-field error message map. A field key is present only when the field
 * currently has an error. Consumers should treat absence of a key as
 * "no error" rather than relying on `undefined` checks.
 */
export type FormErrors<TValues> = {
  readonly [K in FormFieldPath<TValues>]?: string;
};

/**
 * Field Touched Map
 *
 * Tracks which fields the user has interacted with (focus → blur). Used
 * by FormItem to decide whether to display a validation error — errors
 * for untouched fields are typically suppressed until form submit to
 * avoid hostile UX.
 */
export type FormTouched<TValues> = {
  readonly [K in FormFieldPath<TValues>]?: boolean;
};

/**
 * Form Validation Outcome
 *
 * Result of `validate()`. `valid` is the canonical predicate; `errors`
 * is the same object also stored in form state for direct rendering.
 */
export interface FormValidationOutcome<TValues> {
  readonly valid: boolean;
  readonly errors: FormErrors<TValues>;
}

/**
 * useForm Options
 *
 * Configuration accepted by `Form.useForm<T>(options?)`.
 * All properties are optional; defaults produce a fully usable empty form.
 */
export interface UseFormOptions<TValues> {
  /**
   * Initial Values
   *
   * Seed values applied on mount and on `reset()`. When omitted the form
   * starts with `{}` (cast to `TValues`) — callers that omit this MUST
   * ensure their `TValues` type is fully optional or accept `undefined`
   * field reads until the user types.
   */
  readonly initialValues?: Partial<TValues>;

  /**
   * Validators
   *
   * Per-field validator map. Validators are invoked:
   * - On `setFieldValue` only if the field is already touched.
   * - On every `validate()` call (and therefore on submit).
   */
  readonly validators?: FormValidators<TValues>;

  /**
   * Validate on Change
   *
   * When `true` (default), `setFieldValue` re-runs the field's validator
   * after touch. When `false`, validation is deferred to submit only.
   *
   * @default true
   */
  readonly validateOnChange?: boolean;
}

/**
 * Form Instance
 *
 * The object returned by `Form.useForm<T>()`. This is the **only** sanctioned
 * form-state surface for plugins — manual `useState` per field is forbidden
 * by the v3.3.0 stability plan §6.2.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Instance is created by `useForm()` and lives for the component's lifetime.</li>
 *   <li>Plugins read `values`, `errors`, `touched` for rendering.</li>
 *   <li>Plugins call `setFieldValue` / `setFieldTouched` from input handlers.</li>
 *   <li>Plugins call `submit(handler)` from the Form's `onSubmit`.</li>
 *   <li>Plugins call `reset()` to revert to `initialValues`.</li>
 * </ol>
 *
 * <h3>Why a Mutable-Looking API on Immutable State</h3>
 * The setter methods feel imperative (`setFieldValue`) but internally they
 * dispatch to a React reducer — values, errors, and touched maps are always
 * fresh references when accessed via the same hook instance. This shields
 * plugins from React 18 concurrency hazards (stale closures, batching).
 */
export interface FormInstance<TValues> {
  /** Current form values. Reference changes whenever any field changes. */
  readonly values: TValues;

  /** Current per-field error messages. Empty object when fully valid. */
  readonly errors: FormErrors<TValues>;

  /** Per-field touched flags. */
  readonly touched: FormTouched<TValues>;

  /**
   * Whether the form has been submitted at least once. After first submit
   * all fields are considered touched (errors render unconditionally).
   */
  readonly submitCount: number;

  /**
   * Whether a submit handler is currently in flight.
   * Consumers SHOULD pair this with `useSubmitGuard` for button disabling.
   */
  readonly isSubmitting: boolean;

  /**
   * Whether the current values differ from `initialValues`. Useful for
   * "Discard changes?" confirmations.
   */
  readonly isDirty: boolean;

  /**
   * Aggregate validity flag. `true` when `errors` is empty AND no async
   * validation is in progress.
   */
  readonly isValid: boolean;

  /** Get a single field value (typed). */
  getFieldValue<K extends FormFieldPath<TValues>>(name: K): TValues[K];

  /** Get all field values (returns the same reference as `values`). */
  getFieldsValue(): TValues;

  /** Set a single field value, optionally re-validating. */
  setFieldValue<K extends FormFieldPath<TValues>>(name: K, value: TValues[K]): void;

  /** Bulk-set multiple field values at once (single render). */
  setFieldsValue(patch: Partial<TValues>): void;

  /** Mark a field as touched / untouched. Called from input `onBlur`. */
  setFieldTouched<K extends FormFieldPath<TValues>>(name: K, touched?: boolean): void;

  /** Replace the error for one field (used for server-side validation). */
  setFieldError<K extends FormFieldPath<TValues>>(name: K, error: string | null): void;

  /** Replace the entire errors map (used for server-side validation). */
  setErrors(errors: FormErrors<TValues>): void;

  /**
   * Validate one or more fields. Returns the resulting outcome.
   * When `names` is omitted, validates every registered field.
   */
  validate(names?: ReadonlyArray<FormFieldPath<TValues>>): Promise<FormValidationOutcome<TValues>>;

  /**
   * Run a submit handler.
   *
   * <ol>
   *   <li>Marks every field as touched.</li>
   *   <li>Awaits `validate()`.</li>
   *   <li>If valid, awaits `handler(values)`. `isSubmitting` is `true` for the duration.</li>
   *   <li>If `handler` throws, the error is rethrown so callers / `useSubmitGuard` can react.</li>
   * </ol>
   */
  submit<TResult = void>(handler: (values: TValues) => TResult | Promise<TResult>): Promise<TResult | undefined>;

  /** Reset values, errors, and touched to the initial state. */
  reset(nextInitialValues?: Partial<TValues>): void;
}

/**
 * Form Component Type
 *
 * Compound component contract: the Form is both a React function component
 * (`FC<FormProps>`) AND a namespace exposing the `useForm` hook. This
 * matches the plan wording `useUI().Form.useForm()` literally.
 *
 * <h3>Implementation Note for Adapter Authors</h3>
 * Adapters SHOULD NOT re-implement the form state machine. They MUST attach
 * the canonical implementation imported from `@brix-sdk/runtime-sdk-react`:
 *
 * ```ts
 * import { useFormImpl } from '@brix-sdk/runtime-sdk-react';
 * (MuiForm as MuiFormCompound).useForm = useFormImpl;
 * ```
 *
 * This keeps the contract single-sourced and prevents adapter divergence.
 */
export interface FormComponentType<TFormProps = FormProps> {
  (props: TFormProps): import('react').ReactElement | null;
  displayName?: string;
  /**
   * Create a new form instance bound to the calling component's lifetime.
   * Must be called inside a React function component (it is a hook).
   */
  useForm<TValues = Record<string, unknown>>(
    options?: UseFormOptions<TValues>,
  ): FormInstance<TValues>;
}
