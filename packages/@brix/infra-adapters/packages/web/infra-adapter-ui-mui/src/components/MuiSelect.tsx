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
 * @file MUI Select Component
 * @description Material UI implementation of SelectProps from UIAdapter contract.
 *              Dropdown selection with single/multiple mode and search support.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiSelect
 * @version 3.1.0
 *
 * [Design Principles]
 * - Direct mapping from SelectProps to MUI Select/Autocomplete API
 * - Supports single and multiple selection modes
 * - Searchable mode uses MUI Autocomplete
 * - Clearable support with clear button
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This is an atomic component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for form building.
 */

import type { FC } from 'react';
import { useCallback } from 'react';
import type { SelectProps, ComponentSize, SelectOption } from '@brix-sdk/runtime-sdk-api-web';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import MuiSelect from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import FormHelperText from '@mui/material/FormHelperText';
import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import Chip from '@mui/material/Chip';

// ============================================================================
// Size Mappings
// ============================================================================

/**
 * Maps UIAdapter ComponentSize to MUI FormControl size
 */
const SIZE_MAP: Record<ComponentSize, 'small' | 'medium'> = {
  small: 'small',
  medium: 'medium',
  large: 'medium',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Select Component
 *
 * <p>Material UI implementation of SelectProps from UIAdapter contract.
 * Provides dropdown selection with support for single/multiple selection
 * and optional search functionality.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Single and multiple selection modes</li>
 *   <li>Searchable mode via MUI Autocomplete</li>
 *   <li>Clearable option to reset selection</li>
 *   <li>Error state with helper text</li>
 *   <li>Full accessibility via MUI</li>
 * </ul>
 *
 * <h3>Implementation Notes:</h3>
 * <ul>
 *   <li>Non-searchable: Uses MUI Select with MenuItem</li>
 *   <li>Searchable: Uses MUI Autocomplete with TextField</li>
 *   <li>Multiple mode renders chips for selected items</li>
 * </ul>
 *
 * @example
 * ```tsx
 * // Basic single select
 * const { Select } = useUI();
 *
 * <Select
 *   label="Country"
 *   options={countries}
 *   value={selectedCountry}
 *   onChange={setSelectedCountry}
 * />
 *
 * // Multiple select with search
 * <Select
 *   label="Tags"
 *   options={tags}
 *   value={selectedTags}
 *   onChange={setSelectedTags}
 *   multiple
 *   searchable
 * />
 * ```
 *
 * @param props - SelectProps from UIAdapter contract
 * @returns MUI Select or Autocomplete component
 */
export const MuiSelectComponent: FC<SelectProps> = ({
  options,
  value,
  defaultValue,
  multiple = false,
  searchable = false,
  label,
  placeholder,
  helperText,
  error = false,
  disabled = false,
  required = false,
  size = 'medium',
  fullWidth = false,
  clearable = false,
  name,
  onChange,
  onSearch,
  style,
  className,
  'data-testid': dataTestId,
}) => {
  // Generate unique ID for label association
  const labelId = label ? `select-label-${label.replace(/\s+/g, '-')}` : undefined;

  /**
   * Handle change for standard MUI Select
   *
   * <p>Normalizes the event value to match SelectProps onChange signature.</p>
   */
  const handleSelectChange = useCallback(
    (event: { target: { value: unknown } }) => {
      if (onChange) {
        onChange(event.target.value as string | number | Array<string | number>);
      }
    },
    [onChange]
  );

  /**
   * Handle search input change for Autocomplete
   */
  const handleInputChange = useCallback(
    (_event: unknown, inputValue: string) => {
      if (onSearch) {
        onSearch(inputValue);
      }
    },
    [onSearch]
  );

  // ========================================
  // Searchable Mode: Use Autocomplete
  // ========================================
  if (searchable) {
    // Multiple mode with Autocomplete
    if (multiple) {
      const selectedOptions = options.filter((opt) =>
        Array.isArray(value) ? value.includes(opt.value) : false
      );

      const defaultOptions = defaultValue
        ? options.filter((opt) =>
            Array.isArray(defaultValue) ? defaultValue.includes(opt.value) : false
          )
        : undefined;

      return (
        <Autocomplete<SelectOption, true, boolean>
          options={options}
          value={selectedOptions}
          defaultValue={defaultOptions}
          multiple
          disabled={disabled}
          disableClearable={!clearable}
          size={SIZE_MAP[size]}
          fullWidth={fullWidth}
          getOptionLabel={(option) => option.label}
          getOptionDisabled={(option) => option.disabled ?? false}
          isOptionEqualToValue={(option, val) => option.value === val.value}
          onChange={(_event, newValue) => {
            if (onChange) {
              const values = newValue?.map((opt) => opt.value) ?? [];
              onChange(values);
            }
          }}
          onInputChange={handleInputChange}
          style={style}
          className={className}
          renderInput={(params) => (
            <TextField
              {...params}
              label={label}
              placeholder={placeholder}
              error={error}
              required={required}
              helperText={helperText}
              name={name}
              inputProps={{
                ...params.inputProps,
                'data-testid': dataTestId,
              }}
            />
          )}
          renderTags={(tagValue, getTagProps) =>
            tagValue.map((option, index) => (
              <Chip
                {...getTagProps({ index })}
                key={option.value}
                label={option.label}
                size="small"
              />
            ))
          }
        />
      );
    }

    // Single mode with Autocomplete
    const selectedOption = options.find((opt) => opt.value === value) ?? null;
    const defaultOption = defaultValue
      ? options.find((opt) => opt.value === defaultValue)
      : undefined;

    return (
      <Autocomplete<SelectOption, false, boolean>
        options={options}
        value={selectedOption}
        defaultValue={defaultOption}
        multiple={false}
        disabled={disabled}
        disableClearable={!clearable}
        size={SIZE_MAP[size]}
        fullWidth={fullWidth}
        getOptionLabel={(option) => option.label}
        getOptionDisabled={(option) => option.disabled ?? false}
        isOptionEqualToValue={(option, val) => option.value === val.value}
        onChange={(_event, newValue) => {
          if (onChange) {
            onChange(newValue?.value ?? '');
          }
        }}
        onInputChange={handleInputChange}
        style={style}
        className={className}
        renderInput={(params) => (
          <TextField
            {...params}
            label={label}
            placeholder={placeholder}
            error={error}
            required={required}
            helperText={helperText}
            name={name}
            inputProps={{
              ...params.inputProps,
              'data-testid': dataTestId,
            }}
          />
        )}
      />
    );
  }

  // ========================================
  // Standard Mode: Use MUI Select
  // ========================================
  return (
    <FormControl
      size={SIZE_MAP[size]}
      fullWidth={fullWidth}
      error={error}
      disabled={disabled}
      required={required}
      style={style}
      className={className}
    >
      {label && <InputLabel id={labelId}>{label}</InputLabel>}
      <MuiSelect
        labelId={labelId}
        value={value ?? (multiple ? [] : '')}
        defaultValue={defaultValue}
        multiple={multiple}
        label={label}
        name={name}
        onChange={handleSelectChange}
        data-testid={dataTestId}
        displayEmpty={!!placeholder}
        renderValue={(selected) => {
          // Handle placeholder when nothing selected
          if (
            (multiple && Array.isArray(selected) && selected.length === 0) ||
            (!multiple && !selected)
          ) {
            return placeholder ? (
              <span style={{ color: 'rgba(0, 0, 0, 0.38)' }}>{placeholder}</span>
            ) : null;
          }

          // Render selected value(s)
          if (multiple && Array.isArray(selected)) {
            return selected
              .map((val) => options.find((opt) => opt.value === val)?.label ?? val)
              .join(', ');
          }

          return options.find((opt) => opt.value === selected)?.label ?? selected;
        }}
      >
        {options.map((option) => (
          <MenuItem
            key={option.value}
            value={option.value}
            disabled={option.disabled}
            data-testid={option['data-testid']}
          >
            {option.label}
          </MenuItem>
        ))}
      </MuiSelect>
      {helperText && <FormHelperText>{helperText}</FormHelperText>}
    </FormControl>
  );
};

export { MuiSelectComponent as MuiSelect };
export default MuiSelectComponent;
