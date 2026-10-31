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
import java.util.Map;
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
    private static final Pattern QUERY_TYPE_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+$");
    private static final Pattern STORAGE_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,30}$");
    private static final Pattern SQL_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private static final Pattern ACTION_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private static final Pattern SCHEMA_REF_PATTERN = Pattern.compile(
        "^brix://schemas/[a-z][a-z0-9-]*/[a-z][a-z0-9-]*/\\d+\\.\\d+\\.\\d+$");

    /**
     * Runtime custodians root blueprint appendix A registers ({@code manifest.custodialWrites.runtimeCustodians}).
     * Only these may appear in {@code data.custodialWrites[].custodian}.
     */
    public static final Set<String> RUNTIME_CUSTODIANS = Set.of("runtime.tenant-policy-audit",
        "runtime.message-operations");
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
        validateQueries(manifest, result);
        validateCommands(manifest, result);
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

    private boolean requires(PluginManifest manifest, String capability) {
        String simpleName = capability.substring(capability.lastIndexOf('.') + 1);
        return capabilities(manifest, true).stream()
            .anyMatch(ref -> ref != null && (capability.equals(ref.getId()) || simpleName.equals(ref.getId())));
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

    /**
     * Queries are named by their type, which starts with the providing plugin's id (root BRX-RS-MANIFEST-025,
     * MANIFEST-026). The request and response schemas carry the same version, the version of the query's contract;
     * a plugin reads its own data through its own repositories, so it does not consume a query it provides; and a
     * consumer declares the capability it calls queries through.
     */
    private void validateQueries(PluginManifest manifest, ValidationResult result) {
        List<PluginManifest.ContractRef> provides = manifest.getQueries() != null
            ? manifest.getQueries().getProvides() : List.of();
        List<PluginManifest.ContractRef> consumes = manifest.getQueries() != null
            ? manifest.getQueries().getConsumes() : List.of();
        String pluginId = manifest.getMetadata() != null ? manifest.getMetadata().getPluginId() : null;
        Set<String> provided = new HashSet<>();
        Set<String> handlers = new HashSet<>();
        for (int i = 0; i < provides.size(); i++) {
            PluginManifest.ContractRef ref = provides.get(i);
            String prefix = "queries.provides[" + i + "]";
            if (ref == null) {
                result.addError(prefix, "Query provider is required");
                continue;
            }
            requirePattern(result, prefix + ".queryType", ref.getQueryType(), QUERY_TYPE_PATTERN);
            if (pluginId != null && ref.getQueryType() != null && !ref.getQueryType().startsWith(pluginId + ".")) {
                result.addError(prefix + ".queryType", "A provided query is named <pluginId>.<name>: "
                    + ref.getQueryType());
            }
            if (ref.getQueryType() != null && !provided.add(ref.getQueryType() + "#" + majorOf(ref.getRequest()))) {
                result.addError(prefix + ".queryType", "Duplicate provided query type: " + ref.getQueryType());
            }
            requirePresent(result, prefix + ".handlerId", ref.getHandlerId());
            if (ref.getHandlerId() != null && !handlers.add(ref.getHandlerId())) {
                result.addError(prefix + ".handlerId", "Duplicate query handler id: " + ref.getHandlerId());
            }
            String request = schemaRefOf(ref.getRequest());
            String response = schemaRefOf(ref.getResponse());
            requireSchemaRef(result, prefix + ".request.schemaRef", request);
            requireSchemaRef(result, prefix + ".response.schemaRef", response);
            if (request != null && response != null && !versionOf(request).equals(versionOf(response))) {
                result.addError(prefix + ".response.schemaRef", "The request and response schemas of a query carry "
                    + "the version of its contract, so they must be the same: " + versionOf(request) + " and "
                    + versionOf(response));
            }
        }
        Set<String> consumed = new HashSet<>();
        for (int i = 0; i < consumes.size(); i++) {
            PluginManifest.ContractRef ref = consumes.get(i);
            String prefix = "queries.consumes[" + i + "]";
            if (ref == null) {
                result.addError(prefix, "Query consumer is required");
                continue;
            }
            requirePattern(result, prefix + ".queryType", ref.getQueryType(), QUERY_TYPE_PATTERN);
            if (ref.getQueryType() != null && !consumed.add(ref.getQueryType())) {
                result.addError(prefix + ".queryType", "Duplicate consumed query type: " + ref.getQueryType());
            }
            if (pluginId != null && ref.getQueryType() != null && ref.getQueryType().startsWith(pluginId + ".")) {
                result.addError(prefix + ".queryType", "A plugin reads its own data through its own repositories, "
                    + "not through its own query: " + ref.getQueryType());
            }
            if (ref.getVersionRange() != null && !ref.getVersionRange().isBlank()) {
                validateRange(result, prefix + ".versionRange", ref.getVersionRange());
            } else {
                result.addError(prefix + ".versionRange", "A consumed query names the versions it reads");
            }
            if (ref.getRequired() == null) {
                result.addError(prefix + ".required", "A consumed query says whether the plugin requires it");
            }
        }
        if (!consumes.isEmpty() && !declares(manifest, "io.runtime.sdk.capability.QueryCapability")) {
            result.addError("capabilities", "Query consumers must declare io.runtime.sdk.capability.QueryCapability");
        }
    }

    private void requireSchemaRef(ValidationResult result, String field, String schemaRef) {
        if (schemaRef == null || !SCHEMA_REF_PATTERN.matcher(schemaRef).matches()) {
            result.addError(field, "Expected brix://schemas/<plugin>/<name>/<major.minor.patch>");
        }
    }

    private static String schemaRefOf(Map<String, Object> schema) {
        Object ref = schema == null ? null : schema.get("schemaRef");
        return ref instanceof String text ? text : null;
    }

    private static String versionOf(String schemaRef) {
        return schemaRef.substring(schemaRef.lastIndexOf('/') + 1);
    }

    private static String majorOf(Map<String, Object> schema) {
        String ref = schemaRefOf(schema);
        if (ref == null || !SCHEMA_REF_PATTERN.matcher(ref).matches()) {
            return "";
        }
        String version = versionOf(ref);
        return version.substring(0, version.indexOf('.'));
    }

    private boolean declares(PluginManifest manifest, String capability) {
        String simpleName = capability.substring(capability.lastIndexOf('.') + 1);
        return java.util.stream.Stream.concat(capabilities(manifest, true).stream(), capabilities(manifest, false).stream())
            .anyMatch(ref -> ref != null && (capability.equals(ref.getId()) || simpleName.equals(ref.getId())));
    }

    /**
     * Commands are named by their type (root BRX-RS-MESSAGE-010). A provider handles them in its own transaction,
     * so it keeps an inbox and requires persistence (MESSAGE-014); a consumer writes them to its own outbox
     * (MESSAGE-011).
     */
    private void validateCommands(PluginManifest manifest, ValidationResult result) {
        List<PluginManifest.ContractRef> provides = manifest.getCommands() != null
            ? manifest.getCommands().getProvides() : List.of();
        List<PluginManifest.ContractRef> consumes = manifest.getCommands() != null
            ? manifest.getCommands().getConsumes() : List.of();
        Set<String> provided = new HashSet<>();
        Set<String> handlers = new HashSet<>();
        for (int i = 0; i < provides.size(); i++) {
            PluginManifest.ContractRef ref = provides.get(i);
            String prefix = "commands.provides[" + i + "]";
            if (ref == null) {
                result.addError(prefix, "Command provider is required");
                continue;
            }
            requirePresent(result, prefix + ".commandType", ref.getCommandType());
            requirePresent(result, prefix + ".handlerId", ref.getHandlerId());
            if (ref.getCommandType() != null && !provided.add(ref.getCommandType())) {
                result.addError(prefix + ".commandType", "Duplicate provided command type: " + ref.getCommandType());
            }
            if (ref.getHandlerId() != null && !handlers.add(ref.getHandlerId())) {
                result.addError(prefix + ".handlerId", "Duplicate command handler id: " + ref.getHandlerId());
            }
            Object schemaRef = ref.getRequest() == null ? null : ref.getRequest().get("schemaRef");
            if (!(schemaRef instanceof String text) || !text.startsWith("brix://schemas/")) {
                result.addError(prefix + ".request.schemaRef", "A provided command names its request schema brix://schemas/...");
            }
        }
        Set<String> consumed = new HashSet<>();
        for (int i = 0; i < consumes.size(); i++) {
            PluginManifest.ContractRef ref = consumes.get(i);
            String prefix = "commands.consumes[" + i + "]";
            if (ref == null) {
                result.addError(prefix, "Command consumer is required");
                continue;
            }
            requirePresent(result, prefix + ".commandType", ref.getCommandType());
            if (ref.getCommandType() != null && !consumed.add(ref.getCommandType())) {
                result.addError(prefix + ".commandType", "Duplicate consumed command type: " + ref.getCommandType());
            }
            if (ref.getVersionRange() != null && !ref.getVersionRange().isBlank()) {
                validateRange(result, prefix + ".versionRange", ref.getVersionRange());
            } else {
                result.addError(prefix + ".versionRange", "A consumed command names the versions it sends");
            }
        }
        List<PluginManifest.EventSubscribe> subscribes = manifest.getEvents() != null
            ? manifest.getEvents().getSubscribes() : List.of();
        for (PluginManifest.EventSubscribe subscribe : subscribes) {
            if (subscribe != null && subscribe.getHandlerId() != null && handlers.contains(subscribe.getHandlerId())) {
                // The inbox records each delivery by handler id: one id serves one subscription or one command.
                result.addError("commands.provides", "Handler id " + subscribe.getHandlerId()
                    + " serves both an event subscription and a command");
            }
        }
        PluginManifest.DataSection data = manifest.getData();
        if (!provides.isEmpty()) {
            if (data == null || !hasText(data.getInbox())) {
                result.addError("data.inbox", "Command providers must declare data.inbox");
            }
            if (!requires(manifest, "io.runtime.sdk.capability.PersistenceCapability")) {
                result.addError("capabilities.required", "Command providers must require PersistenceCapability");
            }
        }
        if (!consumes.isEmpty() && (data == null || !hasText(data.getOutbox()))) {
            result.addError("data.outbox", "Command consumers must declare data.outbox");
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

        // Root BRX-RS-MANIFEST-025: a subscription with no-durable-idempotency keeps no inbox.
        boolean keepsInbox = subscribes.stream()
            .anyMatch(subscribe -> !"no-durable-idempotency".equals(subscribe.getIdempotencyPolicyRef()));
        if (keepsInbox && !hasText(manifest.getData() != null ? manifest.getData().getInbox() : null)) {
            result.addError("data.inbox", "Event subscribers that keep a persistent inbox must declare data.inbox");
        }
        // Root BRX-RS-MESSAGE-025: the inbox record and the handler's effects share the Owner's transaction.
        if (keepsInbox && !requires(manifest, "io.runtime.sdk.capability.PersistenceCapability")) {
            result.addError("capabilities.required",
                "Event subscribers that keep a persistent inbox must require PersistenceCapability");
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
        // Root BRX-RS-MESSAGE-023: the canonical messaging tables are named after the storage id.
        if (hasText(data.getStorageId())) {
            if (hasText(data.getOutbox()) && !data.getOutbox().equals(data.getStorageId() + "_outbox")) {
                result.addError("data.outbox", "the outbox must be named " + data.getStorageId() + "_outbox");
            }
            if (hasText(data.getInbox()) && !data.getInbox().equals(data.getStorageId() + "_inbox")) {
                result.addError("data.inbox", "the inbox must be named " + data.getStorageId() + "_inbox");
            }
        }
        validateCustodialWrites(data, result);
    }

    /** Root BRX-RS-MANIFEST-031. Whether the table is the owner's is checked where the owner's mapping is known. */
    private void validateCustodialWrites(PluginManifest.DataSection data, ValidationResult result) {
        List<PluginManifest.CustodialWrite> writes = data.getCustodialWrites();
        if (!writes.isEmpty() && !hasText(data.getStorageId())) {
            result.addError("data.custodialWrites", "Custodial writes need data.storageId: only a Data Owner registers them");
        }
        Set<String> registered = new HashSet<>();
        for (int i = 0; i < writes.size(); i++) {
            PluginManifest.CustodialWrite write = writes.get(i);
            String field = "data.custodialWrites[" + i + "]";
            requirePattern(result, field + ".table", write.getTable(), SQL_NAME_PATTERN);
            if (!RUNTIME_CUSTODIANS.contains(write.getCustodian())) {
                result.addError(field + ".custodian", "custodian must be one of " + RUNTIME_CUSTODIANS);
            }
            if (!List.of("INSERT").equals(write.getOperations())) {
                result.addError(field + ".operations", "operations must be [INSERT]");
            }
            if (write.getColumns().isEmpty()
                    || new HashSet<>(write.getColumns()).size() != write.getColumns().size()
                    || !write.getColumns().stream().allMatch(column -> column != null && SQL_NAME_PATTERN.matcher(column).matches())) {
                result.addError(field + ".columns", "columns must list distinct column names");
            }
            if (!write.getActions().stream().allMatch(action -> action != null && ACTION_PATTERN.matcher(action).matches())) {
                result.addError(field + ".actions", "actions must be action ids");
            }
            requirePattern(result, field + ".recordSchemaRef", write.getRecordSchemaRef(), SCHEMA_REF_PATTERN);
            if (!registered.add(write.getTable() + "|" + write.getCustodian())) {
                result.addError(field, "table and custodian are registered more than once");
            }
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
