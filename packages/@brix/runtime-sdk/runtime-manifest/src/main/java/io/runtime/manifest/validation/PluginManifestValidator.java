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
        validateEvents(manifest, result);
        validateInternalContracts(manifest.getInternalContracts(), result);
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

    private void validateEvents(PluginManifest manifest, ValidationResult result) {
        List<PluginManifest.EventPublish> publishes = manifest.getEvents() != null
            ? manifest.getEvents().getPublishes() : List.of();
        List<PluginManifest.EventSubscribe> subscribes = manifest.getEvents() != null
            ? manifest.getEvents().getSubscribes() : List.of();

        boolean persistentPublisher = false;
        Set<String> publishIds = new HashSet<>();
        for (int i = 0; i < publishes.size(); i++) {
            PluginManifest.EventPublish publish = publishes.get(i);
            String prefix = "events.publishes[" + i + "]";
            if (publish == null) {
                result.addError(prefix, "Event publish declaration is required");
                continue;
            }
            requirePresent(result, prefix + ".eventType", publish.getEventType());
            requirePresent(result, prefix + ".schemaRef", publish.getSchemaRef());
            validateSemVer(result, prefix + ".version", publish.getVersion());
            requireReliability(result, prefix + ".reliability", publish.getReliability());
            if (isPersistentReliability(publish.getReliability())) {
                persistentPublisher = true;
            }
            if (publish.getEventType() != null && !publishIds.add(publish.getEventType())) {
                result.addError(prefix + ".eventType", "Duplicate published event type: " + publish.getEventType());
            }
        }

        if (persistentPublisher && !hasText(manifest.getData() != null ? manifest.getData().getOutbox() : null)) {
            result.addError("data.outbox", "CRITICAL/STANDARD event publishers must declare data.outbox");
        }
        if (persistentPublisher && !hasText(manifest.getData() != null ? manifest.getData().getStorageId() : null)) {
            result.addError("data.storageId", "CRITICAL/STANDARD event publishers must declare data.storageId");
        }

        Set<String> subscriptionIds = new HashSet<>();
        Set<String> handlerIds = new HashSet<>();
        for (int i = 0; i < subscribes.size(); i++) {
            PluginManifest.EventSubscribe subscribe = subscribes.get(i);
            String prefix = "events.subscribes[" + i + "]";
            if (subscribe == null) {
                result.addError(prefix, "Event subscription declaration is required");
                continue;
            }
            requirePresent(result, prefix + ".subscriptionId", subscribe.getSubscriptionId());
            requirePresent(result, prefix + ".eventType", subscribe.getEventType());
            validateRange(result, prefix + ".schemaRange", subscribe.getSchemaRange());
            requirePresent(result, prefix + ".handlerId", subscribe.getHandlerId());
            requirePresent(result, prefix + ".retryPolicyRef", subscribe.getRetryPolicyRef());
            requirePresent(result, prefix + ".idempotencyPolicyRef", subscribe.getIdempotencyPolicyRef());
            if (subscribe.getSubscriptionId() != null && !subscriptionIds.add(subscribe.getSubscriptionId())) {
                result.addError(prefix + ".subscriptionId",
                    "Duplicate subscription id: " + subscribe.getSubscriptionId());
            }
            if (subscribe.getHandlerId() != null && !handlerIds.add(subscribe.getHandlerId())) {
                result.addError(prefix + ".handlerId", "Duplicate event handler id: " + subscribe.getHandlerId());
            }
        }

        if (!subscribes.isEmpty() && !hasText(manifest.getData() != null ? manifest.getData().getInbox() : null)) {
            result.addError("data.inbox", "Event subscribers must declare data.inbox");
        }
        if (!subscribes.isEmpty() && !hasText(manifest.getData() != null ? manifest.getData().getStorageId() : null)) {
            result.addError("data.storageId", "Event subscribers must declare data.storageId");
        }
    }

    private void validateRoutes(PluginManifest manifest, ValidationResult result) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < manifest.endpointDeclarations().size(); i++) {
            PluginManifest.Route route = manifest.endpointDeclarations().get(i);
            String prefix = manifest.getEndpoints() != null && !manifest.getEndpoints().getProvides().isEmpty()
                ? "endpoints.provides[" + i + "]"
                : "routes[" + i + "]";
            if (route == null) {
                result.addError(prefix, "Endpoint declaration is required");
                continue;
            }
            requirePresent(result, prefix + ".endpointId", route.getEndpointId());
            requirePresent(result, prefix + ".path", route.getPath());
            requirePresent(result, prefix + ".method", route.getMethod());
            if (route.getEndpointId() != null && !ids.add(route.getEndpointId())) {
                result.addError(prefix + ".endpointId", "Duplicate endpoint id: " + route.getEndpointId());
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

    private void validateInternalContracts(
            PluginManifest.InternalContractSection contracts,
            ValidationResult result) {
        if (contracts == null) {
            return;
        }
        Set<String> providedIds = new HashSet<>();
        for (int i = 0; i < contracts.getProvides().size(); i++) {
            PluginManifest.ProvidedInternalContract provided = contracts.getProvides().get(i);
            String prefix = "internalContracts.provides[" + i + "]";
            if (provided == null) {
                result.addError(prefix, "Provided internal contract is required");
                continue;
            }
            requirePresent(result, prefix + ".contractId", provided.getContractId());
            requirePresent(result, prefix + ".contractType", provided.getContractType());
            validateSemVer(result, prefix + ".contractVersion", provided.getContractVersion());
            requirePresent(result, prefix + ".providerId", provided.getProviderId());
            requirePresent(result, prefix + ".owner", provided.getOwner());
            if (provided.getContractId() != null && !providedIds.add(provided.getContractId())) {
                result.addError(prefix + ".contractId", "Duplicate internal contract id: "
                    + provided.getContractId());
            }
        }
        Set<String> requiredIds = new HashSet<>();
        for (int i = 0; i < contracts.getRequires().size(); i++) {
            PluginManifest.RequiredInternalContract required = contracts.getRequires().get(i);
            String prefix = "internalContracts.requires[" + i + "]";
            if (required == null) {
                result.addError(prefix, "Required internal contract is required");
                continue;
            }
            requirePresent(result, prefix + ".contractId", required.getContractId());
            requirePresent(result, prefix + ".contractType", required.getContractType());
            validateRange(result, prefix + ".versionRange", required.getVersionRange());
            if (required.getRequired() == null) {
                result.addError(prefix + ".required", "Value is required");
            }
            requirePresent(result, prefix + ".privilegeAllowlistRef", required.getPrivilegeAllowlistRef());
            if (required.getContractId() != null && !requiredIds.add(required.getContractId())) {
                result.addError(prefix + ".contractId", "Duplicate internal contract id: "
                    + required.getContractId());
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

    private void requireReliability(ValidationResult result, String field, String value) {
        requirePresent(result, field, value);
        if (value == null || value.isBlank()) {
            return;
        }
        if (!"CRITICAL".equals(value) && !"STANDARD".equals(value) && !"BEST_EFFORT".equals(value)) {
            result.addError(field, "Expected one of CRITICAL, STANDARD, BEST_EFFORT");
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

    private boolean isPersistentReliability(String reliability) {
        return "CRITICAL".equals(reliability) || "STANDARD".equals(reliability);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
