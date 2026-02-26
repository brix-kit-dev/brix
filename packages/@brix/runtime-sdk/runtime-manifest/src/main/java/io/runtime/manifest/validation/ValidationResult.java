/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.manifest.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validation Result.
 *
 * <p>Contains detailed validation results including errors and warnings.</p>
 * <p>包含验证的详细结果，包括错误和警告信息。</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class ValidationResult {

    /**
     * Error list.
     * 错误列表
     */
    private final List<ValidationError> errors;

    /**
     * Warning list.
     * 警告列表
     */
    private final List<ValidationError> warnings;

    /**
     * Creates a validation result.
     */
    public ValidationResult() {
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    /**
     * Creates a validation result.
     *
     * @param errors Error list
     * @param warnings Warning list
     */
    public ValidationResult(List<ValidationError> errors, List<ValidationError> warnings) {
        this.errors = new ArrayList<>(errors);
        this.warnings = new ArrayList<>(warnings);
    }

    /**
     * Checks if validation passed.
     *
     * @return true if there are no errors
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Checks if there are warnings.
     *
     * @return true if there are warnings
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Adds an error.
     *
     * @param error Validation error
     */
    public void addError(ValidationError error) {
        errors.add(error);
    }

    /**
     * Adds an error.
     *
     * @param field Field name
     * @param message Error message
     */
    public void addError(String field, String message) {
        errors.add(new ValidationError(field, message, ValidationSeverity.ERROR));
    }

    /**
     * Adds a warning.
     *
     * @param warning Validation warning
     */
    public void addWarning(ValidationError warning) {
        warnings.add(warning);
    }

    /**
     * Adds a warning.
     *
     * @param field Field name
     * @param message Warning message
     */
    public void addWarning(String field, String message) {
        warnings.add(new ValidationError(field, message, ValidationSeverity.WARNING));
    }

    /**
     * Gets error list.
     *
     * @return Unmodifiable error list
     */
    public List<ValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * Gets warning list.
     *
     * @return Unmodifiable warning list
     */
    public List<ValidationError> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * Gets error message.
     *
     * @return Formatted error message
     */
    public String getErrorMessage() {
        if (isValid()) {
            return "Validation passed";
        }

        StringBuilder sb = new StringBuilder("Validation failed with ");
        sb.append(errors.size()).append(" error(s):\n");
        
        for (ValidationError error : errors) {
            sb.append("  - [").append(error.getField()).append("] ").append(error.getMessage()).append("\n");
        }
        
        return sb.toString();
    }

    /**
     * Merges another validation result.
     *
     * @param other Another validation result
     * @return Merged result (current instance)
     */
    public ValidationResult merge(ValidationResult other) {
        if (other != null) {
            this.errors.addAll(other.errors);
            this.warnings.addAll(other.warnings);
        }
        return this;
    }

    /**
     * Creates a successful validation result.
     *
     * @return Successful validation result
     */
    public static ValidationResult success() {
        return new ValidationResult();
    }

    /**
     * Creates a failed validation result.
     *
     * @param field Field name
     * @param message Error message
     * @return Failed validation result
     */
    public static ValidationResult failure(String field, String message) {
        ValidationResult result = new ValidationResult();
        result.addError(field, message);
        return result;
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
               "valid=" + isValid() +
               ", errors=" + errors.size() +
               ", warnings=" + warnings.size() +
               '}';
    }
}
