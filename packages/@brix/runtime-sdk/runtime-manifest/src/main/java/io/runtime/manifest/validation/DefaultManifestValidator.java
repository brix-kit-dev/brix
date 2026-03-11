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

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.manifest.model.ModuleManifest;
import io.runtime.manifest.model.ModuleManifest.ModuleDependency;
import io.runtime.manifest.model.ModuleManifest.ModuleInfo;

/**
 * Default Manifest Validator Implementation.
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class DefaultManifestValidator implements ManifestValidator {

    private static final Logger logger = LoggerFactory.getLogger(DefaultManifestValidator.class);

    /**
     * Module ID format: letters, numbers, hyphens, underscores, length 3-64.
     */
    private static final Pattern MODULE_ID_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{2,63}$");

    /**
     * Semantic versioning format.
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile(
        "^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9]+)?(\\+[a-zA-Z0-9]+)?$"
    );

    /**
     * Permission format: capability:action.
     */
    private static final Pattern PERMISSION_PATTERN = Pattern.compile("^[a-zA-Z]+:[a-zA-Z]+$");

    /**
     * {@inheritDoc}
     */
    @Override
    public ValidationResult validate(ModuleManifest manifest) {
        ValidationResult result = new ValidationResult();

        if (manifest == null) {
            result.addError("manifest", "Manifest cannot be null");
            return result;
        }

        // Validate module basic info
        validateModuleInfo(manifest.getModule(), result);

        // Validate dependencies
        validateDependencies(manifest, result);

        // Validate permissions
        validatePermissions(manifest, result);

        logger.debug("Manifest validation completed: {}", result);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ValidationResult validateStrict(ModuleManifest manifest) {
        ValidationResult result = validate(manifest);

        if (manifest != null) {
            // Strict mode: check recommended fields
            ModuleInfo module = manifest.getModule();
            if (module != null) {
                if (isBlank(module.getDescription())) {
                    result.addWarning("module.description", "Description is recommended");
                }
                if (isBlank(module.getAuthor())) {
                    result.addWarning("module.author", "Author is recommended");
                }
                if (isBlank(module.getLicense())) {
                    result.addWarning("module.license", "License is recommended");
                }
            }

            // Check runtime configuration
            if (manifest.getRuntime() == null) {
                result.addWarning("runtime", "Runtime configuration is recommended");
            }
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateOrThrow(ModuleManifest manifest) {
        ValidationResult result = validate(manifest);
        if (!result.isValid()) {
            throw new ManifestValidationException(result);
        }
    }

    /**
     * Validates module basic info.
     */
    private void validateModuleInfo(ModuleInfo module, ValidationResult result) {
        if (module == null) {
            result.addError("module", "Module info is required");
            return;
        }

        // Validate module ID
        if (isBlank(module.getId())) {
            result.addError("module.id", "Module ID is required");
        } else if (!MODULE_ID_PATTERN.matcher(module.getId()).matches()) {
            result.addError("module.id",
                "Invalid module ID format. Must start with letter, " +
                "contain only letters, numbers, hyphens, underscores, length 3-64");
        }

        // Validate module name
        if (isBlank(module.getName())) {
            result.addError("module.name", "Module name is required");
        }

        // Validate version
        if (isBlank(module.getVersion())) {
            result.addError("module.version", "Module version is required");
        } else if (!VERSION_PATTERN.matcher(module.getVersion()).matches()) {
            result.addWarning("module.version", 
                "Version does not follow semantic versioning (x.y.z)");
        }
    }

    /**
     * Validates dependencies.
     */
    private void validateDependencies(ModuleManifest manifest, ValidationResult result) {
        if (manifest.getDependencies() == null) {
            return;
        }

        for (int i = 0; i < manifest.getDependencies().size(); i++) {
            ModuleDependency dep = manifest.getDependencies().get(i);
            String prefix = "dependencies[" + i + "]";

            if (isBlank(dep.getModuleId())) {
                result.addError(prefix + ".moduleId", "Dependency module ID is required");
            } else if (!MODULE_ID_PATTERN.matcher(dep.getModuleId()).matches()) {
                result.addWarning(prefix + ".moduleId", "Invalid module ID format");
            }

            // Check self-reference
            if (manifest.getModuleId() != null && 
                manifest.getModuleId().equals(dep.getModuleId())) {
                result.addError(prefix + ".moduleId", "Module cannot depend on itself");
            }
        }
    }

    /**
     * Validates permissions.
     */
    private void validatePermissions(ModuleManifest manifest, ValidationResult result) {
        if (manifest.getPermissions() == null) {
            return;
        }

        for (int i = 0; i < manifest.getPermissions().size(); i++) {
            String permission = manifest.getPermissions().get(i);
            String prefix = "permissions[" + i + "]";

            if (isBlank(permission)) {
                result.addError(prefix, "Permission cannot be empty");
            } else if (!PERMISSION_PATTERN.matcher(permission).matches()) {
                result.addWarning(prefix, 
                    "Permission format should be 'capability:action', got: " + permission);
            }
        }
    }

    /**
     * Checks if string is blank.
     */
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
