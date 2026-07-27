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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import io.runtime.manifest.model.PluginManifest;

/**
 * Validator for the v3.0.10 plugin manifest contract.
 *
 * <p>This validator intentionally checks invariant rules that are independent of
 * a particular Host or infrastructure implementation. JSON Schema remains the
 * structural gate used by tooling and runtime boot; this class gives Java
 * callers stable fail-fast validation without depending on the orchestrator.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class PluginManifestValidator {

    /**
     * Current plugin manifest API version.
     */
    public static final String API_VERSION = "brix.io/v1";

    /**
     * Required classpath resource path for active plugin manifests.
     */
    public static final String ACTIVE_MANIFEST_RESOURCE = "META-INF/brix/plugin-manifest.yaml";

    private static final Pattern PLUGIN_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*$");
    private static final Pattern STORAGE_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,30}$");
    private static final Pattern SEMVER_PATTERN = Pattern.compile(
        "^\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?(?:\\+[0-9A-Za-z][0-9A-Za-z.-]*)?$");
    private static final Pattern RANGE_TOKEN_PATTERN = Pattern.compile(
        "^(>=|>|<=|<|=)?\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?$");

    /**
     * Validates the manifest and returns all violations.
     *
     * @param manifest plugin manifest
     * @return validation result
     */
    public ValidationResult validate(PluginManifest manifest) {
        ValidationResult result = new ValidationResult();
        if (manifest == null) {
            result.addError("manifest", "Plugin manifest is required");
            return result;
        }

        requireEquals(result, "apiVersion", manifest.getApiVersion(), API_VERSION);
        requireEquals(result, "kind", manifest.getKind(), "Plugin");
        validateMetadata(manifest.getMetadata(), result);
        validateRuntime(manifest.getRuntime(), result);
        validateCapabilities("capabilities.required", capabilities(manifest, true), result);
        validateCapabilities("capabilities.optional", capabilities(manifest, false), result);
        validateContractRefs("queries.provides", manifest.getQueries() != null
            ? manifest.getQueries().getProvides() : List.of(), result);
        validateContractRefs("queries.consumes", manifest.getQueries() != null
            ? manifest.getQueries().getConsumes() : List.of(), result);
        validateContractRefs("commands.provides", manifest.getCommands() != null
            ? manifest.getCommands().getProvides() : List.of(), result);
        validateContractRefs("commands.consumes", manifest.getCommands() != null
            ? manifest.getCommands().getConsumes() : List.of(), result);
        validateContractRefs("events.publishes", manifest.getEvents() != null
            ? manifest.getEvents().getPublishes() : List.of(), result);
        validateContractRefs("events.subscribes", manifest.getEvents() != null
            ? manifest.getEvents().getSubscribes() : List.of(), result);
        validateRoutes(manifest, result);
        validateData(manifest.getData(), result);
        validateConfig(manifest.getConfig(), result);
        return result;
    }

    /**
     * Validates the manifest and throws on any error.
     *
     * @param manifest plugin manifest
     */
    public void validateOrThrow(PluginManifest manifest) {
        ValidationResult result = validate(manifest);
        if (!result.isValid()) {
            throw new ManifestValidationException(result);
        }
    }

    private void validateMetadata(PluginManifest.Metadata metadata, ValidationResult result) {
        if (metadata == null) {
            result.addError("metadata", "metadata is required");
            return;
        }
        requirePattern(result, "metadata.pluginId", metadata.getPluginId(), PLUGIN_ID_PATTERN);
        requirePresent(result, "metadata.name", metadata.getName());
        validateSemVer(result, "metadata.version", metadata.getVersion());
        requirePresent(result, "metadata.vendor", metadata.getVendor());
        requirePresent(result, "metadata.license", metadata.getLicense());
    }

    private void validateRuntime(PluginManifest.Runtime runtime, ValidationResult result) {
        if (runtime == null) {
            result.addError("runtime", "runtime is required");
            return;
        }
        validateSemVer(result, "runtime.compiledAgainst", runtime.getCompiledAgainst());
        validateRange(result, "runtime.supportedRange", runtime.getSupportedRange());
    }

    private List<PluginManifest.CapabilityRef> capabilities(PluginManifest manifest, boolean required) {
        if (manifest.getCapabilities() == null) {
            return List.of();
        }
        return required ? manifest.getCapabilities().getRequired() : manifest.getCapabilities().getOptional();
    }

    private void validateCapabilities(
            String field,
            List<PluginManifest.CapabilityRef> capabilities,
            ValidationResult result) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < capabilities.size(); i++) {
            PluginManifest.CapabilityRef ref = capabilities.get(i);
            String prefix = field + "[" + i + "]";
            if (ref == null) {
                result.addError(prefix, "Capability reference is required");
                continue;
            }
            requirePresent(result, prefix + ".id", ref.getId());
            validateRange(result, prefix + ".version", ref.getVersion());
            if (ref.getId() != null && !ids.add(ref.getId())) {
                result.addError(prefix + ".id", "Duplicate capability id: " + ref.getId());
            }
        }
    }

    private void validateContractRefs(
            String field,
            List<PluginManifest.ContractRef> refs,
            ValidationResult result) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < refs.size(); i++) {
            PluginManifest.ContractRef ref = refs.get(i);
            String prefix = field + "[" + i + "]";
            if (ref == null) {
                result.addError(prefix, "Contract reference is required");
                continue;
            }
            requirePresent(result, prefix + ".id", ref.getId());
            if (ref.getVersion() != null && !ref.getVersion().isBlank()) {
                validateSemVer(result, prefix + ".version", ref.getVersion());
            }
            if (ref.getId() != null && !ids.add(ref.getId())) {
                result.addError(prefix + ".id", "Duplicate contract id: " + ref.getId());
            }
        }
    }

    private void validateRoutes(PluginManifest manifest, ValidationResult result) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < manifest.getRoutes().size(); i++) {
            PluginManifest.Route route = manifest.getRoutes().get(i);
            String prefix = "routes[" + i + "]";
            if (route == null) {
                result.addError(prefix, "Route is required");
                continue;
            }
            requirePresent(result, prefix + ".id", route.getId());
            requirePresent(result, prefix + ".path", route.getPath());
            requirePresent(result, prefix + ".method", route.getMethod());
            if (route.getId() != null && !ids.add(route.getId())) {
                result.addError(prefix + ".id", "Duplicate route id: " + route.getId());
            }
        }
    }

    private void validateData(PluginManifest.DataSection data, ValidationResult result) {
        if (data == null) {
            return;
        }
        if (data.getStorageId() != null && !STORAGE_ID_PATTERN.matcher(data.getStorageId()).matches()) {
            result.addError("data.storageId", "storageId must match [a-z][a-z0-9_]{0,30}");
        }
    }

    private void validateConfig(List<PluginManifest.ConfigEntry> config, ValidationResult result) {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < config.size(); i++) {
            PluginManifest.ConfigEntry entry = config.get(i);
            String prefix = "config[" + i + "]";
            if (entry == null) {
                result.addError(prefix, "Config entry is required");
                continue;
            }
            requirePresent(result, prefix + ".key", entry.getKey());
            requirePresent(result, prefix + ".type", entry.getType());
            if (entry.getKey() != null && !keys.add(entry.getKey())) {
                result.addError(prefix + ".key", "Duplicate config key: " + entry.getKey());
            }
            if (isSecret(entry.getSensitivity()) && entry.getDefaultValue() != null) {
                result.addError(prefix + ".defaultValue", "Secret config entries must not declare defaults");
            }
        }
    }

    private void requireEquals(ValidationResult result, String field, String actual, String expected) {
        if (!expected.equals(actual)) {
            result.addError(field, "Expected " + expected);
        }
    }

    private void requirePresent(ValidationResult result, String field, String value) {
        if (value == null || value.isBlank()) {
            result.addError(field, "Value is required");
        }
    }

    private void requirePattern(ValidationResult result, String field, String value, Pattern pattern) {
        requirePresent(result, field, value);
        if (value != null && !value.isBlank() && !pattern.matcher(value).matches()) {
            result.addError(field, "Invalid value: " + value);
        }
    }

    private void validateSemVer(ValidationResult result, String field, String value) {
        requirePattern(result, field, value, SEMVER_PATTERN);
    }

    private void validateRange(ValidationResult result, String field, String value) {
        requirePresent(result, field, value);
        if (value == null || value.isBlank()) {
            return;
        }
        for (String token : value.trim().split("\\s+")) {
            if (!RANGE_TOKEN_PATTERN.matcher(token).matches()) {
                result.addError(field, "Invalid Brix Range v1 token: " + token);
            }
        }
    }

    private boolean isSecret(String sensitivity) {
        return sensitivity != null && sensitivity.equalsIgnoreCase("secret");
    }
}
