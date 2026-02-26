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

/**
 * Validation Error.
 * 验证错误
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class ValidationError {

    /**
     * Field name.
     * 字段名
     */
    private final String field;

    /**
     * Error message.
     * 错误消息
     */
    private final String message;

    /**
     * Severity level.
     * 严重级别
     */
    private final ValidationSeverity severity;

    /**
     * Creates a validation error.
     *
     * @param field Field name
     * @param message Error message
     * @param severity Severity level
     */
    public ValidationError(String field, String message, ValidationSeverity severity) {
        this.field = field;
        this.message = message;
        this.severity = severity;
    }

    /**
     * Gets field name.
     *
     * @return Field name
     */
    public String getField() {
        return field;
    }

    /**
     * Gets error message.
     *
     * @return Error message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets severity level.
     *
     * @return Severity level
     */
    public ValidationSeverity getSeverity() {
        return severity;
    }

    @Override
    public String toString() {
        return "[" + severity + "] " + field + ": " + message;
    }
}
