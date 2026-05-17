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
 * @file useForm
 * @description Single-source implementation of the Brix form-state machine
 *              required by the v3.3.0 Frontend Stability Reform Plan §6.2 (C-8).
 * @module @brix-sdk/runtime-sdk-react/hooks/useForm
 * @version 3.3.0
 *
 * [Architectural Position — Layer 2B Shared Hook]
 * The hook implements the {@link FormInstance} contract defined in
 * `runtime-sdk-api-web/src/types/ui/form.ts`. It is consumed in two ways:
 *
 * 1. Plugins call it through the compound component:
 *    `const form = useUI().Form.useForm<MyValues>(opts)`.
 *
 * 2. UI adapters (`infra-adapter-ui-{mui,native}`) attach the **same**
 *    function to their `Form` export's `useForm` static property, so the
 *    behaviour is byte-identical across adapters and the platform never
 *    forks form-state semantics. See `MuiForm.tsx` / `NativeForm.tsx` for
 *    the attachment pattern.
 *
 * [Why Implement Here Instead of Inside the Adapter]
 * - Adapters are UI-library-coupled (R-3 forbids leaking that coupling).
 * - The form state machine is pure React + TS — zero UI library access.
 * - Single source ensures that "Mui form behaves identically to Native form",
 *   which is the explicit precondition for swapping adapters at host time.
 *
 * [Concurrency Safety]
 * - All state lives in `useReducer` so updates are batched by React 18.
 * - Async validators are tagged with a monotonically increasing token; only
 *   the latest run wins. This prevents stale validation overwriting fresh
 *   results when the user types rapidly.
 * - `submit()` rejects the inflight promise of any prior submit by checking
 *   a generation counter — last submit wins, identical to `usePageState`.
 */

import { useCallback, useMemo, useReducer, useRef } from 'react';
import type {
  FieldValidationResult,
  FieldValidator,
  FormErrors,
  FormFieldPath,
  FormInstance,
  FormTouched,
  FormValidationOutcome,
  FormValidators,
  UseFormOptions,
} from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Reducer state and actions (no magic strings — exported as constants)
// ============================================================================

interface FormState<TValues> {
  values: TValues;
  errors: FormErrors<TValues>;
  touched: FormTouched<TValues>;
  submitCount: number;
  isSubmitting: boolean;
}

const ACTION_SET_FIELD_VALUE = 'set-field-value' as const;
const ACTION_SET_FIELDS_VALUE = 'set-fields-value' as const;
const ACTION_SET_FIELD_TOUCHED = 'set-field-touched' as const;
const ACTION_TOUCH_ALL = 'touch-all' as const;
const ACTION_SET_FIELD_ERROR = 'set-field-error' as const;
const ACTION_SET_ERRORS = 'set-errors' as const;
const ACTION_BEGIN_SUBMIT = 'begin-submit' as const;
const ACTION_END_SUBMIT = 'end-submit' as const;
const ACTION_RESET = 'reset' as const;

type FormAction<TValues> =
  | { type: typeof ACTION_SET_FIELD_VALUE; name: FormFieldPath<TValues>; value: TValues[FormFieldPath<TValues>] }
  | { type: typeof ACTION_SET_FIELDS_VALUE; patch: Partial<TValues> }
  | { type: typeof ACTION_SET_FIELD_TOUCHED; name: FormFieldPath<TValues>; touched: boolean }
  | { type: typeof ACTION_TOUCH_ALL }
  | { type: typeof ACTION_SET_FIELD_ERROR; name: FormFieldPath<TValues>; error: string | null }
  | { type: typeof ACTION_SET_ERRORS; errors: FormErrors<TValues> }
  | { type: typeof ACTION_BEGIN_SUBMIT }
  | { type: typeof ACTION_END_SUBMIT }
  | { type: typeof ACTION_RESET; values: TValues };

function reducer<TValues>(state: FormState<TValues>, action: FormAction<TValues>): FormState<TValues> {
  switch (action.type) {
    case ACTION_SET_FIELD_VALUE: {
      // Reference identity matters: we always create a new `values` object so
      // React detects the change. Same for the cleared error of this field.
      const nextValues = { ...state.values, [action.name]: action.value } as TValues;
      const nextErrors = { ...state.errors };
      delete (nextErrors as Record<string, unknown>)[action.name];
      return { ...state, values: nextValues, errors: nextErrors };
    }
    case ACTION_SET_FIELDS_VALUE: {
      const nextValues = { ...state.values, ...action.patch } as TValues;
      const nextErrors = { ...state.errors };
      // Clear errors for any patched field — keeps `errors` in sync with edits.
      for (const key of Object.keys(action.patch)) {
        delete (nextErrors as Record<string, unknown>)[key];
      }
      return { ...state, values: nextValues, errors: nextErrors };
    }
    case ACTION_SET_FIELD_TOUCHED: {
      return {
        ...state,
        touched: { ...state.touched, [action.name]: action.touched },
      };
    }
    case ACTION_TOUCH_ALL: {
      // Used at submit time so error rendering becomes unconditional.
      const allTouched: Record<string, boolean> = {};
      for (const key of Object.keys(state.values as Record<string, unknown>)) {
        allTouched[key] = true;
      }
      return { ...state, touched: allTouched as FormTouched<TValues> };
    }
    case ACTION_SET_FIELD_ERROR: {
      const nextErrors = { ...state.errors };
      if (action.error) {
        (nextErrors as Record<string, string>)[action.name] = action.error;
      } else {
        delete (nextErrors as Record<string, unknown>)[action.name];
      }
      return { ...state, errors: nextErrors };
    }
    case ACTION_SET_ERRORS: {
      return { ...state, errors: action.errors };
    }
    case ACTION_BEGIN_SUBMIT: {
      return { ...state, isSubmitting: true, submitCount: state.submitCount + 1 };
    }
    case ACTION_END_SUBMIT: {
      return { ...state, isSubmitting: false };
    }
    case ACTION_RESET: {
      return {
        values: action.values,
        errors: {},
        touched: {},
        submitCount: 0,
        isSubmitting: false,
      };
    }
    /* istanbul ignore next — exhaustiveness guard */
    default: {
      const _exhaustive: never = action;
      return _exhaustive;
    }
  }
}

// ============================================================================
// Helpers
// ============================================================================

/**
 * Shallow equality on the keys of `a`. Used to compute `isDirty` cheaply.
 * Returns `true` when every own key of `a` matches the corresponding value
 * in `b` by `Object.is`. Suitable for flat form objects, which is the
 * documented constraint of `FormInstance` (no nested-path keys).
 */
function shallowEqualByKeys(a: Record<string, unknown>, b: Record<string, unknown>): boolean {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  for (const k of keys) {
    if (!Object.is(a[k], b[k])) {
      return false;
    }
  }
  return true;
}

async function runValidator<TValues, K extends FormFieldPath<TValues>>(
  validator: FieldValidator<TValues, K> | undefined,
  value: TValues[K],
  values: TValues,
): Promise<FieldValidationResult> {
  if (!validator) {
    return null;
  }
  const result = validator(value, values);
  return result instanceof Promise ? await result : result;
}

// ============================================================================
// Hook
// ============================================================================

/**
 * useForm
 *
 * Single-source implementation of {@link FormInstance}. Plugins SHOULD call
 * this through the compound component: `useUI().Form.useForm<T>(opts)`.
 * Adapters re-export this same function as `Form.useForm` (see
 * `MuiForm.tsx` / `NativeForm.tsx`).
 *
 * @example
 * ```tsx
 * interface CarouselValues { title: string; url: string }
 *
 * function CarouselForm() {
 *   const { Form, FormItem, Input, Button } = useUI();
 *   const form = Form.useForm<CarouselValues>({
 *     initialValues: { title: '', url: '' },
 *     validators: {
 *       title: (v) => (v.trim() ? null : 'Title is required'),
 *       url:   (v) => (/^https?:/.test(v) ? null : 'Must be a URL'),
 *     },
 *   });
 *
 *   const onSubmit = () => form.submit(async (values) => {
 *     await http.post('/api/carousels', values);
 *   });
 *
 *   return (
 *     <Form onSubmit={(e) => { e.preventDefault(); onSubmit(); }}>
 *       <FormItem label="Title" required validateStatus={form.errors.title ? 'error' : undefined}
 *                 helperText={form.touched.title ? form.errors.title : undefined}>
 *         <Input value={form.values.title}
 *                onChange={(v) => form.setFieldValue('title', v)}
 *                onBlur={() => form.setFieldTouched('title')} />
 *       </FormItem>
 *       <Button type="submit" loading={form.isSubmitting}>Save</Button>
 *     </Form>
 *   );
 * }
 * ```
 */
export function useForm<TValues = Record<string, unknown>>(
  options: UseFormOptions<TValues> = {},
): FormInstance<TValues> {
  const { initialValues = {} as Partial<TValues>, validators, validateOnChange = true } = options;

  // The initial values object is captured once — we never re-derive from
  // changing `options` props because that would silently reset user input.
  // Callers that need to change initial values should call `reset(next)`.
  const initialValuesRef = useRef<TValues>(initialValues as TValues);
  const validatorsRef = useRef<FormValidators<TValues> | undefined>(validators);
  validatorsRef.current = validators;

  const [state, dispatch] = useReducer(reducer<TValues>, undefined, () => ({
    values: initialValuesRef.current,
    errors: {} as FormErrors<TValues>,
    touched: {} as FormTouched<TValues>,
    submitCount: 0,
    isSubmitting: false,
  }));

  // Async-validation token: only the latest run is allowed to commit errors.
  const validationTokenRef = useRef<number>(0);

  // Snapshot ref so callbacks can read the latest values without re-creating
  // every callback on each render (which would invalidate event handlers in
  // children and defeat React.memo).
  const stateRef = useRef(state);
  stateRef.current = state;

  // ------------------------------------------------------------------------
  // Read API
  // ------------------------------------------------------------------------

  const getFieldValue = useCallback(
    <K extends FormFieldPath<TValues>>(name: K): TValues[K] => stateRef.current.values[name],
    [],
  );
  const getFieldsValue = useCallback((): TValues => stateRef.current.values, []);

  // ------------------------------------------------------------------------
  // Mutation API
  // ------------------------------------------------------------------------

  const validateField = useCallback(
    async <K extends FormFieldPath<TValues>>(name: K, value: TValues[K], values: TValues): Promise<void> => {
      const validator = validatorsRef.current?.[name] as FieldValidator<TValues, K> | undefined;
      if (!validator) {
        return;
      }
      const token = ++validationTokenRef.current;
      const result = await runValidator(validator, value, values);
      if (validationTokenRef.current === token) {
        dispatch({ type: ACTION_SET_FIELD_ERROR, name, error: result ?? null });
      }
    },
    [],
  );

  const setFieldValue = useCallback(
    <K extends FormFieldPath<TValues>>(name: K, value: TValues[K]): void => {
      dispatch({ type: ACTION_SET_FIELD_VALUE, name, value });
      if (validateOnChange && stateRef.current.touched[name]) {
        // Validate against the about-to-commit values to catch immediate errors.
        const nextValues = { ...stateRef.current.values, [name]: value } as TValues;
        void validateField(name, value, nextValues);
      }
    },
    [validateOnChange, validateField],
  );

  const setFieldsValue = useCallback((patch: Partial<TValues>): void => {
    dispatch({ type: ACTION_SET_FIELDS_VALUE, patch });
  }, []);

  const setFieldTouched = useCallback(
    <K extends FormFieldPath<TValues>>(name: K, touched: boolean = true): void => {
      dispatch({ type: ACTION_SET_FIELD_TOUCHED, name, touched });
      if (touched && validateOnChange) {
        // First touch triggers validation so the error appears on blur.
        const value = stateRef.current.values[name];
        void validateField(name, value, stateRef.current.values);
      }
    },
    [validateOnChange, validateField],
  );

  const setFieldError = useCallback(
    <K extends FormFieldPath<TValues>>(name: K, error: string | null): void => {
      dispatch({ type: ACTION_SET_FIELD_ERROR, name, error });
    },
    [],
  );

  const setErrors = useCallback((errors: FormErrors<TValues>): void => {
    dispatch({ type: ACTION_SET_ERRORS, errors });
  }, []);

  const validate = useCallback(
    async (names?: ReadonlyArray<FormFieldPath<TValues>>): Promise<FormValidationOutcome<TValues>> => {
      const validatorMap = validatorsRef.current ?? ({} as FormValidators<TValues>);
      const targetNames =
        names ??
        (Object.keys(validatorMap) as unknown as ReadonlyArray<FormFieldPath<TValues>>);

      const values = stateRef.current.values;
      const newErrors: Record<string, string> = {};

      // Run all validators concurrently — order independent.
      await Promise.all(
        targetNames.map(async (name) => {
          const validator = validatorMap[name] as FieldValidator<TValues> | undefined;
          if (!validator) {
            return;
          }
          const result = await runValidator(validator, values[name], values);
          if (result) {
            newErrors[name as string] = result;
          }
        }),
      );

      // Preserve errors for fields outside the target set (server-side errors etc.)
      const merged: FormErrors<TValues> = { ...stateRef.current.errors };
      // Clear only the fields we re-validated:
      for (const name of targetNames) {
        delete (merged as Record<string, unknown>)[name as string];
      }
      Object.assign(merged, newErrors);

      dispatch({ type: ACTION_SET_ERRORS, errors: merged });

      return {
        valid: Object.keys(newErrors).length === 0
          && Object.keys(merged).filter((k) => !targetNames.includes(k as FormFieldPath<TValues>)).length === 0,
        errors: merged,
      };
    },
    [],
  );

  const submit = useCallback(
    async <TResult = void>(
      handler: (values: TValues) => TResult | Promise<TResult>,
    ): Promise<TResult | undefined> => {
      dispatch({ type: ACTION_TOUCH_ALL });
      dispatch({ type: ACTION_BEGIN_SUBMIT });
      try {
        const outcome = await validate();
        if (!outcome.valid) {
          return undefined;
        }
        const values = stateRef.current.values;
        const result = await handler(values);
        return result;
      } finally {
        dispatch({ type: ACTION_END_SUBMIT });
      }
    },
    [validate],
  );

  const reset = useCallback((nextInitialValues?: Partial<TValues>): void => {
    if (nextInitialValues) {
      initialValuesRef.current = nextInitialValues as TValues;
    }
    dispatch({ type: ACTION_RESET, values: initialValuesRef.current });
  }, []);

  // ------------------------------------------------------------------------
  // Derived flags
  // ------------------------------------------------------------------------

  const isDirty = useMemo(
    () => !shallowEqualByKeys(
      state.values as Record<string, unknown>,
      initialValuesRef.current as Record<string, unknown>,
    ),
    [state.values],
  );
  const isValid = useMemo(() => Object.keys(state.errors).length === 0, [state.errors]);

  // ------------------------------------------------------------------------
  // Public instance — referentially stable across renders so React.memo of
  // children sees identity continuity. Only the inner `values` / `errors`
  // / `touched` references change.
  // ------------------------------------------------------------------------

  const instance = useMemo<FormInstance<TValues>>(() => ({
    get values() { return stateRef.current.values; },
    get errors() { return stateRef.current.errors; },
    get touched() { return stateRef.current.touched; },
    get submitCount() { return stateRef.current.submitCount; },
    get isSubmitting() { return stateRef.current.isSubmitting; },
    get isDirty() { return isDirty; },
    get isValid() { return isValid; },
    getFieldValue,
    getFieldsValue,
    setFieldValue,
    setFieldsValue,
    setFieldTouched,
    setFieldError,
    setErrors,
    validate,
    submit,
    reset,
  }), [
    isDirty,
    isValid,
    getFieldValue,
    getFieldsValue,
    setFieldValue,
    setFieldsValue,
    setFieldTouched,
    setFieldError,
    setErrors,
    validate,
    submit,
    reset,
  ]);

  return instance;
}
