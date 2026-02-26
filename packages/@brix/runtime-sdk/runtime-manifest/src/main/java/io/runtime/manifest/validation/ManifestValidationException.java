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
 * Manifest Validation Exception.
 *
 * <p>Thrown when manifest validation fails.</p>
 * <p>当 Manifest 验证失败时抛出。</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class ManifestValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Validation result.
     * 验证结果
     */
    private final ValidationResult validationResult;

    /**
     * Creates a manifest validation exception.
     *
     * @param validationResult Validation result
     */
    public ManifestValidationException(ValidationResult validationResult) {
        super(validationResult.getErrorMessage());
        this.validationResult = validationResult;
    }

    /**
     * Creates a manifest validation exception.
     *
     * @param message Exception message
     */
    public ManifestValidationException(String message) {
        super(message);
        this.validationResult = ValidationResult.failure("manifest", message);
    }

    /**
     * Gets validation result.
     *
     * @return Validation result
     */
    public ValidationResult getValidationResult() {
        return validationResult;
    }
}
