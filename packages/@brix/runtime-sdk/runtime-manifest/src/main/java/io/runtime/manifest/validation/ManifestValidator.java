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

import io.runtime.manifest.model.ModuleManifest;

/**
 * Manifest Validator.
 *
 * <p>Responsible for validating manifest completeness and correctness.</p>
 * <p>负责验证 Manifest 的完整性和正确性。</p>
 *
 * <h3>Validation Contents</h3>
 * <ul>
 *   <li>Required field checks (moduleId, name, version)</li>
 *   <li>Format validation (version number, permission identifiers)</li>
 *   <li>Dependency integrity checks</li>
 *   <li>Permission syntax validation</li>
 * </ul>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface ManifestValidator {

    /**
     * Validates a manifest.
     *
     * @param manifest Module manifest
     * @return Validation result
     */
    ValidationResult validate(ModuleManifest manifest);

    /**
     * Validates a manifest in strict mode.
     *
     * <p>Strict mode checks more optional fields and best practices.</p>
     * <p>严格模式会检查更多可选字段和最佳实践</p>
     *
     * @param manifest Module manifest
     * @return Validation result
     */
    ValidationResult validateStrict(ModuleManifest manifest);

    /**
     * Validates a manifest and throws exception on failure.
     *
     * @param manifest Module manifest
     * @throws ManifestValidationException if validation fails
     */
    void validateOrThrow(ModuleManifest manifest);
}
