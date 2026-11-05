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
package io.runtime.orchestrator.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.runtime.orchestrator.endpoint.EndpointAccessPolicy;
import io.runtime.orchestrator.endpoint.TenantGrantTable;
import io.runtime.sdk.capability.registry.CapabilityDescriptor;
import io.runtime.sdk.capability.registry.CapabilityRegistry;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.plugin.PluginIdentity;

/**
 * Runtime-internal plugin descriptor resolved from a plugin manifest.
 *
 * <p>This descriptor is intentionally L2B internal. Plugins receive only
 * {@link io.runtime.sdk.plugin.PluginContext}; Host code receives only bootstrap
 * state. The descriptor carries the minimal declaration set needed by Phase 2
 * to validate capability access and code bindings before the full manifest
 * schema migration phase lands.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class PluginRuntimeDescriptor {

    private final PluginIdentity identity;
    private final String version;
    private final Set<String> requiredCapabilities;
    private final Set<String> optionalCapabilities;
    private final Map<String, EndpointDeclaration> endpoints;
    private final Map<String, String> endpointIdsByHandler;
    private final List<PermissionDeclaration> permissions;
    private final Set<String> queryHandlers;
    private final Set<String> commandHandlers;
    private final Set<String> eventHandlers;
    private final Set<String> tasks;
    private final DataDeclaration data;
    private final Map<String, EventPublication> eventPublications;
    private final Map<String, EventSubscription> eventSubscriptions;
    private final Map<String, SubscriptionPolicies> subscriptionPolicies;
    private final Map<String, String> publicationSchemas;
    private final Map<String, ProvidedCommand> providedCommands;
    private final Map<String, ConsumedCommand> consumedCommands;
    private final Map<String, ProvidedQuery> providedQueries;
    private final Map<String, ConsumedQuery> consumedQueries;
    private final Map<String, ProvidedInternalContract> providedInternalContracts;
    private final Map<String, ProvidedTask> providedTasks;
    private final Map<String, io.runtime.orchestrator.external.ExternalServiceDeclaration> externalServices;

    private PluginRuntimeDescriptor(Builder builder) {
        this.identity = Objects.requireNonNull(builder.identity, "identity must not be null");
        this.version = requireText(builder.version, "version");
        this.requiredCapabilities = copy(builder.requiredCapabilities);
        this.optionalCapabilities = copy(builder.optionalCapabilities);
        this.endpoints = Map.copyOf(builder.endpoints);
        this.endpointIdsByHandler = Map.copyOf(builder.endpointIdsByHandler);
        this.permissions = List.copyOf(builder.permissions);
        this.queryHandlers = copy(builder.queryHandlers);
        this.commandHandlers = copy(builder.commandHandlers);
        this.eventHandlers = copy(builder.eventHandlers);
        this.tasks = copy(builder.tasks);
        this.data = builder.data;
        this.eventPublications = Map.copyOf(builder.eventPublications);
        this.eventSubscriptions = Map.copyOf(builder.eventSubscriptions);
        this.subscriptionPolicies = Map.copyOf(builder.subscriptionPolicies);
        this.publicationSchemas = Map.copyOf(builder.publicationSchemas);
        this.providedCommands = Map.copyOf(builder.providedCommands);
        this.consumedCommands = Map.copyOf(builder.consumedCommands);
        this.providedQueries = Map.copyOf(builder.providedQueries);
        this.consumedQueries = Map.copyOf(builder.consumedQueries);
        this.providedInternalContracts = Map.copyOf(builder.providedInternalContracts);
        this.providedTasks = Map.copyOf(builder.providedTasks);
        this.externalServices = Map.copyOf(builder.externalServices);
    }

    /**
     * Creates a descriptor builder.
     *
     * @param pluginId plugin identifier from the manifest
     * @return descriptor builder
     */
    public static Builder builder(String pluginId) {
        return new Builder(new PluginIdentity(pluginId));
    }

    /**
     * Returns plugin identity.
     *
     * @return plugin identity
     */
    public PluginIdentity identity() {
        return identity;
    }

    /**
     * Returns the plugin manifest version.
     *
     * @return plugin version
     */
    public String version() {
        return version;
    }

    /**
     * Returns required capability declarations.
     *
     * @return immutable required capability declarations
     */
    public Set<String> requiredCapabilities() {
        return requiredCapabilities;
    }

    /**
     * Returns optional capability declarations.
     *
     * @return immutable optional capability declarations
     */
    public Set<String> optionalCapabilities() {
        return optionalCapabilities;
    }

    /**
     * Returns whether the requested capability type is declared as required.
     *
     * @param capabilityType capability contract type
     * @param registry capability registry used for descriptor aliases
     * @return true when manifest declarations match the capability type
     */
    public boolean isRequiredCapability(Class<?> capabilityType, CapabilityRegistry registry) {
        return matches(requiredCapabilities, capabilityType, registry);
    }

    /**
     * Returns whether the requested capability type is declared as optional.
     *
     * @param capabilityType capability contract type
     * @param registry capability registry used for descriptor aliases
     * @return true when manifest declarations match the capability type
     */
    public boolean isOptionalCapability(Class<?> capabilityType, CapabilityRegistry registry) {
        return matches(optionalCapabilities, capabilityType, registry);
    }

    /**
     * Validates that a manifest endpoint id is declared.
     *
     * @param id endpoint id
     */
    public void requireEndpoint(String id) {
        requireDeclared("endpoint", id, endpoints.keySet());
    }

    /**
     * Returns the endpoint a handler id serves: the endpoint whose route names that {@code handlerId}, or the
     * endpoint of that id when no route names it (root BRX-RS-MANIFEST-011 binds endpoints by handler id).
     *
     * @param handlerId handler id, or endpoint id
     * @return the endpoint id
     */
    public String requireEndpointOfHandler(String handlerId) {
        String endpointId = endpointIdsByHandler.getOrDefault(handlerId, handlerId);
        requireDeclared("endpoint", endpointId, endpoints.keySet());
        return endpointId;
    }

    /**
     * Returns endpoint declarations keyed by manifest endpoint id.
     *
     * @return immutable endpoint declarations
     */
    public Map<String, EndpointDeclaration> endpointDeclarations() {
        return endpoints;
    }

    /**
     * Returns the permission catalog entries declared by the manifest, in declaration order.
     *
     * @return immutable permission declarations with their default tenant grants
     */
    public List<PermissionDeclaration> permissionDeclarations() {
        return permissions;
    }

    /**
     * Returns plugin data declaration used by startup policy gates.
     *
     * @return data declaration, possibly empty
     */
    public DataDeclaration data() {
        return data;
    }

    /**
     * Validates that a manifest query handler id is declared.
     *
     * @param id query handler id
     */
    public void requireQueryHandler(String id) {
        requireDeclared("query handler", id, queryHandlers);
    }

    /**
     * Validates that a manifest command handler id is declared.
     *
     * @param id command handler id
     */
    public void requireCommandHandler(String id) {
        requireDeclared("command handler", id, commandHandlers);
    }

    /**
     * Validates that a manifest event handler id is declared.
     *
     * @param id event handler id
     */
    public void requireEventHandler(String id) {
        requireDeclared("event handler", id, eventHandlers);
    }

    /**
     * Returns manifest-declared published events keyed by event id.
     *
     * @return immutable event publication declarations
     */
    public Map<String, EventPublication> eventPublications() {
        return eventPublications;
    }

    /**
     * Returns manifest-declared event subscriptions keyed by handler id.
     *
     * @return immutable event subscription declarations
     */
    public Map<String, EventSubscription> eventSubscriptions() {
        return eventSubscriptions;
    }

    /**
     * Returns the timeout, concurrency and publisher policies of each subscription, by handler id.
     *
     * @return immutable policies
     */
    public Map<String, SubscriptionPolicies> subscriptionPolicies() {
        return subscriptionPolicies;
    }

    /**
     * Returns the {@code schemaRef} of each published event type.
     *
     * @return immutable schema references
     */
    public Map<String, String> publicationSchemas() {
        return publicationSchemas;
    }

    /**
     * Returns the commands the plugin provides, by command type (root BRX-RS-MESSAGE-010).
     *
     * @return immutable provided commands
     */
    public Map<String, ProvidedCommand> providedCommands() {
        return providedCommands;
    }

    /**
     * Returns the commands the plugin submits, by command type.
     *
     * @return immutable consumed commands
     */
    public Map<String, ConsumedCommand> consumedCommands() {
        return consumedCommands;
    }

    /**
     * Returns the tasks the plugin provides, by handler id (root BRX-RS-MANIFEST-025; standard-link plan L6.2).
     *
     * @return immutable provided tasks
     */
    public Map<String, ProvidedTask> providedTasks() {
        return providedTasks;
    }

    /**
     * Returns the external services the plugin calls, by id (root BRX-RS-ERROR-014; standard-link plan L8.2).
     *
     * @return immutable external service declarations
     */
    public Map<String, io.runtime.orchestrator.external.ExternalServiceDeclaration> externalServices() {
        return externalServices;
    }

    /**
     * Returns the queries the plugin provides, by query type and major version, such as
     * {@code sample-shipping.find-shipment#1} (root BRX-RS-MANIFEST-025).
     *
     * @return immutable provided queries
     */
    public Map<String, ProvidedQuery> providedQueries() {
        return providedQueries;
    }

    /**
     * Returns the queries the plugin calls, by query type.
     *
     * @return immutable consumed queries
     */
    public Map<String, ConsumedQuery> consumedQueries() {
        return consumedQueries;
    }

    /**
     * Returns internal contracts provided by this plugin artifact.
     *
     * @return immutable internal contract declarations keyed by contract id
     */
    public Map<String, ProvidedInternalContract> providedInternalContracts() {
        return providedInternalContracts;
    }

    /**
     * Validates that a manifest task id is declared.
     *
     * @param id task id
     */
    public void requireTask(String id) {
        requireDeclared("managed task", id, tasks);
    }

    private boolean matches(Set<String> declarations, Class<?> capabilityType, CapabilityRegistry registry) {
        if (capabilityType == null) {
            return false;
        }
        if (declarations.contains(capabilityType.getName()) || declarations.contains(capabilityType.getSimpleName())) {
            return true;
        }
        if (registry == null) {
            return false;
        }
        return registry.getDescriptor(capabilityType)
            .map(descriptor -> matchesDescriptor(declarations, descriptor))
            .orElse(false);
    }

    private static boolean matchesDescriptor(Set<String> declarations, CapabilityDescriptor descriptor) {
        return declarations.contains(descriptor.getName())
            || declarations.stream().anyMatch(descriptor.getAliases()::contains);
    }

    private void requireDeclared(String kind, String id, Set<String> declarations) {
        if (id == null || id.isBlank()) {
            throw new PluginRuntimeException("Plugin '" + identity.pluginId()
                + "' attempted to bind a blank " + kind + " id");
        }
        if (!declarations.contains(id)) {
            throw new PluginRuntimeException("Plugin '" + identity.pluginId()
                + "' attempted to bind undeclared " + kind + " '" + id + "'");
        }
    }

    private static Set<String> copy(Collection<String> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : source) {
            if (value != null && !value.isBlank()) {
                normalized.add(value);
            }
        }
        return Set.copyOf(normalized);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /**
     * Mutable builder for {@link PluginRuntimeDescriptor}.
     */
    public static final class Builder {

        private final PluginIdentity identity;
        private final Set<String> requiredCapabilities = new LinkedHashSet<>();
        private final Set<String> optionalCapabilities = new LinkedHashSet<>();
        private final Map<String, EndpointDeclaration> endpoints = new LinkedHashMap<>();
        private final Map<String, String> endpointIdsByHandler = new LinkedHashMap<>();
        private final List<PermissionDeclaration> permissions = new ArrayList<>();
        private final Set<String> queryHandlers = new LinkedHashSet<>();
        private final Set<String> commandHandlers = new LinkedHashSet<>();
        private final Set<String> eventHandlers = new LinkedHashSet<>();
        private final Set<String> tasks = new LinkedHashSet<>();
        private DataDeclaration data = new DataDeclaration("", "", "");
        private final Map<String, EventPublication> eventPublications = new LinkedHashMap<>();
        private final Map<String, EventSubscription> eventSubscriptions = new LinkedHashMap<>();
        private final Map<String, SubscriptionPolicies> subscriptionPolicies = new LinkedHashMap<>();
        private final Map<String, String> publicationSchemas = new LinkedHashMap<>();
        private final Map<String, ProvidedCommand> providedCommands = new LinkedHashMap<>();
        private final Map<String, ProvidedTask> providedTasks = new LinkedHashMap<>();
        private final Map<String, io.runtime.orchestrator.external.ExternalServiceDeclaration> externalServices =
            new LinkedHashMap<>();
        private final Map<String, ConsumedCommand> consumedCommands = new LinkedHashMap<>();
        private final Map<String, ProvidedQuery> providedQueries = new LinkedHashMap<>();
        private final Map<String, ConsumedQuery> consumedQueries = new LinkedHashMap<>();
        private final Map<String, ProvidedInternalContract> providedInternalContracts = new LinkedHashMap<>();
        private String version = "0.0.0";

        private Builder(PluginIdentity identity) {
            this.identity = identity;
        }

        /**
         * Adds required capability declarations.
         *
         * @param declarations manifest capability names
         * @return this builder
         */
        public Builder requiredCapabilities(Collection<String> declarations) {
            addAll(requiredCapabilities, declarations);
            return this;
        }

        /**
         * Sets the plugin version from the manifest.
         *
         * @param declaration plugin version
         * @return this builder
         */
        public Builder version(String declaration) {
            if (declaration != null && !declaration.isBlank()) {
                this.version = declaration;
            }
            return this;
        }

        /**
         * Adds optional capability declarations.
         *
         * @param declarations manifest capability names
         * @return this builder
         */
        public Builder optionalCapabilities(Collection<String> declarations) {
            addAll(optionalCapabilities, declarations);
            return this;
        }

        /**
         * Adds declared endpoint ids.
         *
         * @param ids endpoint ids
         * @return this builder
         */
        public Builder endpoints(Collection<String> ids) {
            if (ids != null) {
                for (String id : ids) {
                    endpoint(id, "", "", null);
                }
            }
            return this;
        }

        /**
         * Adds a declared endpoint.
         *
         * @param id endpoint id
         * @param method endpoint method
         * @param path endpoint path template
         * @param accessPolicy declared endpoint access policy
         * @return this builder
         */
        public Builder endpoint(String id, String method, String path, EndpointAccessPolicy accessPolicy) {
            return endpoint(id, method, path, accessPolicy, null);
        }

        /**
         * Declares an endpoint with the request and response contract its manifest names (standard-link plan L7.2,
         * L7.3).
         *
         * @param id endpoint id
         * @param method HTTP method
         * @param path path template
         * @param accessPolicy declared access policy
         * @param contract request and response schemas and the success status; {@code null} for none
         * @return this builder
         */
        public Builder endpoint(String id, String method, String path, EndpointAccessPolicy accessPolicy,
                io.runtime.orchestrator.endpoint.EndpointContract contract) {
            if (id != null && !id.isBlank()) {
                endpoints.put(id, new EndpointDeclaration(id, method, path, accessPolicy, contract));
            }
            return this;
        }

        /**
         * Records the handler id a declared endpoint's route names.
         *
         * @param handlerId the route's handler id
         * @param endpointId the route's endpoint id
         * @return this builder
         */
        public Builder endpointHandler(String handlerId, String endpointId) {
            if (handlerId != null && !handlerId.isBlank() && endpointId != null && !endpointId.isBlank()) {
                String previous = endpointIdsByHandler.putIfAbsent(handlerId, endpointId);
                if (previous != null && !previous.equals(endpointId)) {
                    throw new PluginRuntimeException("Plugin '" + identity.pluginId() + "' names handler '"
                        + handlerId + "' for both endpoint '" + previous + "' and endpoint '" + endpointId + "'");
                }
            }
            return this;
        }

        /**
         * Adds a permission catalog entry with its default tenant grants.
         *
         * @param id permission id
         * @param grants declared grants, validated when the Host builds its grant table
         * @return this builder
         */
        public Builder permission(String id, List<TenantGrantTable.Grant> grants) {
            permissions.add(new PermissionDeclaration(id, grants == null ? List.of() : grants));
            return this;
        }

        /**
         * Adds declared query handler ids.
         *
         * @param ids query handler ids
         * @return this builder
         */
        public Builder queryHandlers(Collection<String> ids) {
            addAll(queryHandlers, ids);
            return this;
        }

        /**
         * Adds declared command handler ids.
         *
         * @param ids command handler ids
         * @return this builder
         */
        public Builder commandHandlers(Collection<String> ids) {
            addAll(commandHandlers, ids);
            return this;
        }

        /**
         * Adds declared event handler ids.
         *
         * @param ids event handler ids
         * @return this builder
         */
        public Builder eventHandlers(Collection<String> ids) {
            addAll(eventHandlers, ids);
            return this;
        }

        /**
         * Adds plugin data ownership metadata.
         *
         * @param storageId storage id
         * @param outbox canonical outbox table
         * @param inbox canonical inbox table
         * @return this builder
         */
        public Builder data(String storageId, String outbox, String inbox) {
            this.data = new DataDeclaration(storageId, outbox, inbox);
            return this;
        }

        /**
         * Declares a command the plugin provides; its handler id is bound in {@code configure()}.
         *
         * @param commandType the command type
         * @param handlerId the handler id
         * @param version the version of the command's request schema
         * @return this builder
         */
        public Builder providedCommand(String commandType, String handlerId, String version) {
            return providedCommand(new ProvidedCommand(commandType, handlerId, version));
        }

        /**
         * Declares a command the plugin provides, with its policies.
         *
         * @param command the declaration
         * @return this builder
         */
        public Builder providedCommand(ProvidedCommand command) {
            if (!command.commandType().isEmpty()) {
                providedCommands.put(command.commandType(), command);
                if (!command.handlerId().isEmpty()) {
                    commandHandlers.add(command.handlerId());
                }
            }
            return this;
        }

        /**
         * Declares a command the plugin submits.
         *
         * @param commandType the command type
         * @param versionRange the versions it sends
         * @param required whether the plugin cannot run without a provider
         * @return this builder
         */
        public Builder consumedCommand(String commandType, String versionRange, boolean required) {
            return consumedCommand(new ConsumedCommand(commandType, versionRange, required));
        }

        /**
         * Declares a command the plugin submits, with its policies.
         *
         * @param command the declaration
         * @return this builder
         */
        public Builder consumedCommand(ConsumedCommand command) {
            if (!command.commandType().isEmpty()) {
                consumedCommands.put(command.commandType(), command);
            }
            return this;
        }

        /**
         * Declares a query the plugin provides; its handler id is bound in {@code configure()}.
         *
         * @param query the declaration
         * @return this builder
         */
        public Builder providedQuery(ProvidedQuery query) {
            if (!query.queryType().isEmpty()) {
                providedQueries.put(query.key(), query);
                if (!query.handlerId().isEmpty()) {
                    queryHandlers.add(query.handlerId());
                }
            }
            return this;
        }

        /**
         * Declares a query the plugin calls.
         *
         * @param query the declaration
         * @return this builder
         */
        public Builder consumedQuery(ConsumedQuery query) {
            if (!query.queryType().isEmpty()) {
                consumedQueries.put(query.queryType(), query);
            }
            return this;
        }

        /**
         * Records the schema a published event type refers to.
         *
         * @param eventType published event type
         * @param schemaRef its {@code schemaRef}
         * @return this builder
         */
        public Builder publicationSchema(String eventType, String schemaRef) {
            if (eventType != null && !eventType.isBlank() && schemaRef != null && !schemaRef.isBlank()) {
                publicationSchemas.put(eventType, schemaRef.trim());
            }
            return this;
        }

        /**
         * Records a subscription's timeout, concurrency and publisher policies.
         *
         * @param handlerId the subscription's handler id
         * @param timeoutPolicyRef timeout policy reference
         * @param concurrencyPolicyRef concurrency policy reference
         * @param publisherPolicyRef publisher policy reference
         * @return this builder
         */
        public Builder subscriptionPolicies(
                String handlerId, String timeoutPolicyRef, String concurrencyPolicyRef, String publisherPolicyRef) {
            if (handlerId != null && !handlerId.isBlank()) {
                subscriptionPolicies.put(handlerId.trim(),
                    new SubscriptionPolicies(timeoutPolicyRef, concurrencyPolicyRef, publisherPolicyRef));
            }
            return this;
        }

        /**
         * Adds a published event declaration.
         *
         * @param id event contract id
         * @param version event schema version
         * @param reliability manifest reliability declaration
         * @return this builder
         */
        public Builder eventPublication(String id, String version, EventReliability reliability) {
            EventPublication publication = new EventPublication(id, version, reliability);
            if (!publication.id().isBlank()) {
                eventPublications.put(publication.id(), publication);
            }
            return this;
        }

        /**
         * Adds an event subscription declaration.
         *
         * @param subscriptionId subscription id
         * @param eventType event contract id
         * @param schemaRange accepted schema range
         * @param handlerId handler id bound by {@code configure()}
         * @param retryPolicyRef retry policy reference
         * @param idempotencyPolicyRef idempotency policy reference
         * @return this builder
         */
        public Builder eventSubscription(
                String subscriptionId,
                String eventType,
                String schemaRange,
                String handlerId,
                String retryPolicyRef,
                String idempotencyPolicyRef) {
            return eventSubscription(subscriptionId, eventType, schemaRange, handlerId, retryPolicyRef,
                idempotencyPolicyRef, "");
        }

        /**
         * Adds an event subscription declaration with its scope policy.
         *
         * @param subscriptionId subscription id
         * @param eventType event contract id
         * @param schemaRange accepted schema range
         * @param handlerId handler id
         * @param retryPolicyRef retry policy reference
         * @param idempotencyPolicyRef idempotency policy reference
         * @param scopePolicyRef scope policy reference
         * @return this builder
         */
        public Builder eventSubscription(
                String subscriptionId,
                String eventType,
                String schemaRange,
                String handlerId,
                String retryPolicyRef,
                String idempotencyPolicyRef,
                String scopePolicyRef) {
            EventSubscription subscription = new EventSubscription(
                subscriptionId,
                eventType,
                schemaRange,
                handlerId,
                retryPolicyRef,
                idempotencyPolicyRef,
                scopePolicyRef);
            if (!subscription.handlerId().isBlank()) {
                eventSubscriptions.put(subscription.handlerId(), subscription);
                eventHandlers.add(subscription.handlerId());
            }
            return this;
        }

        /**
         * Adds a provided internal contract declaration.
         *
         * @param contractId stable internal contract id
         * @param contractType internal contract Java type
         * @param contractVersion semantic contract version
         * @param providerId stable provider id
         * @param owner owning plugin id
         * @return this builder
         */
        public Builder providedInternalContract(
                String contractId,
                String contractType,
                String contractVersion,
                String providerId,
                String owner) {
            return providedInternalContract(contractId, contractType, contractVersion, providerId, owner, Set.of());
        }

        /**
         * Adds a provided internal contract.
         *
         * @param contractId stable contract identifier
         * @param contractType contract interface name
         * @param contractVersion exact contract version
         * @param providerId provider identifier
         * @param owner owning plugin
         * @param replayableWrites the write methods whose results the runtime may keep
         * @return this builder
         */
        public Builder providedInternalContract(
                String contractId,
                String contractType,
                String contractVersion,
                String providerId,
                String owner,
                Collection<String> replayableWrites) {
            if (contractId != null && !contractId.isBlank()) {
                providedInternalContracts.put(contractId, new ProvidedInternalContract(
                    contractId,
                    contractType,
                    contractVersion,
                    providerId,
                    owner,
                    Set.copyOf(replayableWrites)));
            }
            return this;
        }

        /**
         * Adds declared managed task ids.
         *
         * @param ids managed task ids
         * @return this builder
         */
        public Builder tasks(Collection<String> ids) {
            addAll(tasks, ids);
            return this;
        }

        /**
         * Declares a task the plugin provides, with its policies; its handler id becomes a declared task.
         *
         * @param task the declaration
         * @return this builder
         */
        public Builder providedTask(ProvidedTask task) {
            if (!task.handlerId().isEmpty()) {
                providedTasks.put(task.handlerId(), task);
                tasks.add(task.handlerId());
            }
            return this;
        }

        /**
         * Declares an external service the plugin calls (standard-link plan L8.2).
         *
         * @param service the declaration
         * @return this builder
         */
        public Builder externalService(io.runtime.orchestrator.external.ExternalServiceDeclaration service) {
            externalServices.put(Objects.requireNonNull(service, "service must not be null").id(), service);
            return this;
        }

        /**
         * Builds an immutable descriptor.
         *
         * @return descriptor
         */
        public PluginRuntimeDescriptor build() {
            return new PluginRuntimeDescriptor(this);
        }

        private static void addAll(Set<String> target, Collection<String> values) {
            if (values == null) {
                return;
            }
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    target.add(value);
                }
            }
        }
    }

    /**
     * Manifest-declared endpoint routing metadata.
     *
     * @param id endpoint id
     * @param method HTTP method
     * @param path route path template
     * @param accessPolicy declared access policy; {@code null} for legacy
     *        id-only declarations, which cannot be published
     */
    public record EndpointDeclaration(String id, String method, String path, EndpointAccessPolicy accessPolicy,
            io.runtime.orchestrator.endpoint.EndpointContract contract) {

        /**
         * Creates an endpoint declaration.
         */
        public EndpointDeclaration {
            id = id == null ? "" : id.trim();
            method = method == null ? "" : method.trim();
            path = path == null ? "" : path.trim();
            contract = contract != null ? contract : io.runtime.orchestrator.endpoint.EndpointContract.none();
        }

        /**
         * Creates a declaration without a request or response contract.
         */
        public EndpointDeclaration(String id, String method, String path, EndpointAccessPolicy accessPolicy) {
            this(id, method, path, accessPolicy, null);
        }
    }

    /**
     * Manifest-declared permission catalog entry.
     *
     * @param id permission id
     * @param grants declared default grants to built-in tenant roles
     */
    public record PermissionDeclaration(String id, List<TenantGrantTable.Grant> grants) {

        /**
         * Creates a permission declaration.
         */
        public PermissionDeclaration {
            id = id == null ? "" : id.trim();
            grants = List.copyOf(Objects.requireNonNull(grants, "grants must not be null"));
        }
    }

    /**
     * Manifest-declared data ownership metadata for reliable message gates.
     *
     * @param storageId immutable storage id
     * @param outbox canonical outbox table
     * @param inbox canonical inbox table
     */
    public record DataDeclaration(String storageId, String outbox, String inbox) {

        /**
         * Creates a data declaration.
         */
        public DataDeclaration {
            storageId = storageId == null ? "" : storageId.trim();
            outbox = outbox == null ? "" : outbox.trim();
            inbox = inbox == null ? "" : inbox.trim();
        }
    }

    /**
     * Manifest-declared published event contract.
     *
     * @param id event contract id
     * @param version schema version
     * @param reliability reliability policy
     */
    public record EventPublication(String id, String version, EventReliability reliability) {

        /**
         * Creates an event publication declaration.
         */
        public EventPublication {
            id = id == null ? "" : id.trim();
            version = version == null ? "" : version.trim();
            Objects.requireNonNull(reliability, "reliability must not be null");
        }

        /**
         * Returns whether this event requires durable outbox startup gates.
         *
         * @return true for CRITICAL and STANDARD
         */
        public boolean requiresPersistentDelivery() {
            return reliability == EventReliability.CRITICAL || reliability == EventReliability.STANDARD;
        }
    }

    /**
     * Manifest-declared event subscription contract.
     *
     * @param subscriptionId subscription id
     * @param eventType event contract id
     * @param schemaRange accepted schema range
     * @param handlerId handler id
     * @param retryPolicyRef retry policy reference
     * @param idempotencyPolicyRef idempotency policy reference
     * @param scopePolicyRef scope policy reference, such as a registered cross-tenant policy
     */
    public record EventSubscription(
            String subscriptionId,
            String eventType,
            String schemaRange,
            String handlerId,
            String retryPolicyRef,
            String idempotencyPolicyRef,
            String scopePolicyRef) {

        /** The idempotency policy that records each delivered event in the owner's persistent inbox. */
        public static final String PERSISTENT_INBOX = "persistent-inbox";

        /**
         * The idempotency policy of a subscription that keeps no inbox, which only a BEST_EFFORT event type allows
         * (root BRX-RS-MANIFEST-025).
         */
        public static final String NO_DURABLE_IDEMPOTENCY = "no-durable-idempotency";

        /** The idempotency policies a subscription can name (root BRX-RS-MANIFEST-026). */
        public static final Set<String> IDEMPOTENCY_POLICIES = Set.of(PERSISTENT_INBOX, NO_DURABLE_IDEMPOTENCY);

        /**
         * Creates an event subscription declaration.
         */
        public EventSubscription {
            subscriptionId = subscriptionId == null ? "" : subscriptionId.trim();
            eventType = eventType == null ? "" : eventType.trim();
            schemaRange = schemaRange == null ? "" : schemaRange.trim();
            handlerId = handlerId == null ? "" : handlerId.trim();
            retryPolicyRef = retryPolicyRef == null ? "" : retryPolicyRef.trim();
            idempotencyPolicyRef = idempotencyPolicyRef == null ? "" : idempotencyPolicyRef.trim();
            scopePolicyRef = scopePolicyRef == null ? "" : scopePolicyRef.trim();
        }

        /**
         * Creates an event subscription declaration without a scope policy.
         */
        public EventSubscription(
                String subscriptionId,
                String eventType,
                String schemaRange,
                String handlerId,
                String retryPolicyRef,
                String idempotencyPolicyRef) {
            this(subscriptionId, eventType, schemaRange, handlerId, retryPolicyRef, idempotencyPolicyRef, "");
        }

        /**
         * Whether each delivered event is recorded in the owner's persistent inbox.
         *
         * @return {@code false} only for {@link #NO_DURABLE_IDEMPOTENCY}
         */
        public boolean persistentInbox() {
            return !NO_DURABLE_IDEMPOTENCY.equals(idempotencyPolicyRef);
        }
    }

    /**
     * A command the plugin provides.
     *
     * @param commandType the command type
     * @param handlerId its handler id
     * @param version the version of its request schema
     * @param retryPolicyRef retry policy reference
     * @param timeoutPolicyRef timeout policy reference
     * @param concurrencyPolicyRef concurrency policy reference
     * @param scopePolicyRef scope policy reference
     * @param accessPolicyRef access policy reference: who may send the command
     * @param idempotencyPolicyRef idempotency policy reference
     */
    public record ProvidedCommand(String commandType, String handlerId, String version, String retryPolicyRef,
            String timeoutPolicyRef, String concurrencyPolicyRef, String scopePolicyRef, String accessPolicyRef,
            String idempotencyPolicyRef) {

        /**
         * Creates the declaration, blank when not declared.
         */
        public ProvidedCommand {
            commandType = commandType == null ? "" : commandType.trim();
            handlerId = handlerId == null ? "" : handlerId.trim();
            version = version == null ? "" : version.trim();
            retryPolicyRef = retryPolicyRef == null ? "" : retryPolicyRef.trim();
            timeoutPolicyRef = timeoutPolicyRef == null ? "" : timeoutPolicyRef.trim();
            concurrencyPolicyRef = concurrencyPolicyRef == null ? "" : concurrencyPolicyRef.trim();
            scopePolicyRef = scopePolicyRef == null ? "" : scopePolicyRef.trim();
            accessPolicyRef = accessPolicyRef == null ? "" : accessPolicyRef.trim();
            idempotencyPolicyRef = idempotencyPolicyRef == null ? "" : idempotencyPolicyRef.trim();
        }

        /**
         * Creates a declaration with the delivery policies only, as descriptors before 3.3.0 did.
         */
        public ProvidedCommand(String commandType, String handlerId, String version, String retryPolicyRef,
                String timeoutPolicyRef, String concurrencyPolicyRef) {
            this(commandType, handlerId, version, retryPolicyRef, timeoutPolicyRef, concurrencyPolicyRef, "", "", "");
        }

        /**
         * Creates a declaration without policies.
         */
        public ProvidedCommand(String commandType, String handlerId, String version) {
            this(commandType, handlerId, version, "", "", "");
        }
    }

    /**
     * A task the plugin provides (root BRX-RS-MANIFEST-025, DATA-028; standard-link plan L6.2).
     *
     * @param taskId the task id
     * @param handlerId its handler id
     * @param scopePolicyRef scope policy reference
     * @param executionIdentityPolicyRef execution identity policy reference
     * @param triggerPolicyRef trigger policy reference, such as {@code every:PT1H}
     * @param timeoutPolicyRef timeout policy reference
     * @param concurrencyPolicyRef concurrency policy reference
     * @param retryPolicyRef retry policy reference
     * @param idempotencyPolicyRef idempotency policy reference
     * @param transaction the unit of work: {@code required}, {@code read-only} or {@code none}
     */
    public record ProvidedTask(String taskId, String handlerId, String scopePolicyRef, String executionIdentityPolicyRef,
            String triggerPolicyRef, String timeoutPolicyRef, String concurrencyPolicyRef, String retryPolicyRef,
            String idempotencyPolicyRef, String transaction) {

        /** The read-write unit of work, a task's default. */
        public static final String TRANSACTION_REQUIRED = "required";

        /** A read-only unit of work. */
        public static final String TRANSACTION_READ_ONLY = "read-only";

        /** No unit of work: the task touches no data. */
        public static final String TRANSACTION_NONE = "none";

        /**
         * Creates the declaration, blank when not declared; the transaction defaults to {@code required}.
         */
        public ProvidedTask {
            taskId = taskId == null ? "" : taskId.trim();
            handlerId = handlerId == null ? "" : handlerId.trim();
            scopePolicyRef = scopePolicyRef == null ? "" : scopePolicyRef.trim();
            executionIdentityPolicyRef = executionIdentityPolicyRef == null ? "" : executionIdentityPolicyRef.trim();
            triggerPolicyRef = triggerPolicyRef == null ? "" : triggerPolicyRef.trim();
            timeoutPolicyRef = timeoutPolicyRef == null ? "" : timeoutPolicyRef.trim();
            concurrencyPolicyRef = concurrencyPolicyRef == null ? "" : concurrencyPolicyRef.trim();
            retryPolicyRef = retryPolicyRef == null ? "" : retryPolicyRef.trim();
            idempotencyPolicyRef = idempotencyPolicyRef == null ? "" : idempotencyPolicyRef.trim();
            transaction = transaction == null || transaction.isBlank() ? TRANSACTION_REQUIRED : transaction.trim();
        }
    }

    /**
     * A command the plugin submits.
     *
     * @param commandType the command type
     * @param versionRange the versions it sends
     * @param required whether the plugin cannot run without a provider
     * @param timeoutPolicyRef timeout policy reference
     * @param retryPolicyRef retry policy reference
     * @param idempotencyPolicyRef idempotency policy reference
     */
    public record ConsumedCommand(String commandType, String versionRange, boolean required, String timeoutPolicyRef,
            String retryPolicyRef, String idempotencyPolicyRef) {

        /**
         * Creates the declaration, blank when not declared.
         */
        public ConsumedCommand {
            commandType = commandType == null ? "" : commandType.trim();
            versionRange = versionRange == null ? "" : versionRange.trim();
            timeoutPolicyRef = timeoutPolicyRef == null ? "" : timeoutPolicyRef.trim();
            retryPolicyRef = retryPolicyRef == null ? "" : retryPolicyRef.trim();
            idempotencyPolicyRef = idempotencyPolicyRef == null ? "" : idempotencyPolicyRef.trim();
        }

        /**
         * Creates a declaration without policies.
         */
        public ConsumedCommand(String commandType, String versionRange, boolean required) {
            this(commandType, versionRange, required, "", "", "");
        }
    }

    /**
     * A query the plugin provides (root BRX-RS-MANIFEST-025). Its version is the version both its schemas carry.
     *
     * @param queryType the query type, {@code <pluginId>.<name>}
     * @param requestSchemaRef the request schema
     * @param responseSchemaRef the response schema
     * @param handlerId its handler id
     * @param scopePolicyRef scope policy reference
     * @param accessPolicyRef access policy reference
     * @param timeoutPolicyRef timeout policy reference
     * @param concurrencyPolicyRef concurrency policy reference
     */
    public record ProvidedQuery(String queryType, String requestSchemaRef, String responseSchemaRef, String handlerId,
            String scopePolicyRef, String accessPolicyRef, String timeoutPolicyRef, String concurrencyPolicyRef) {

        /**
         * Creates the declaration, blank when not declared.
         */
        public ProvidedQuery {
            queryType = queryType == null ? "" : queryType.trim();
            requestSchemaRef = requestSchemaRef == null ? "" : requestSchemaRef.trim();
            responseSchemaRef = responseSchemaRef == null ? "" : responseSchemaRef.trim();
            handlerId = handlerId == null ? "" : handlerId.trim();
            scopePolicyRef = scopePolicyRef == null ? "" : scopePolicyRef.trim();
            accessPolicyRef = accessPolicyRef == null ? "" : accessPolicyRef.trim();
            timeoutPolicyRef = timeoutPolicyRef == null ? "" : timeoutPolicyRef.trim();
            concurrencyPolicyRef = concurrencyPolicyRef == null ? "" : concurrencyPolicyRef.trim();
        }

        /**
         * Returns the contract version, the version of the request schema.
         *
         * @return {@code major.minor.patch}, or blank when the schema reference has none
         */
        public String version() {
            int slash = requestSchemaRef.lastIndexOf('/');
            return slash < 0 ? "" : requestSchemaRef.substring(slash + 1);
        }

        /**
         * Returns the contract's major version.
         *
         * @return the major version, or {@code -1} when the version is malformed
         */
        public int major() {
            String version = version();
            int dot = version.indexOf('.');
            try {
                return Integer.parseInt(dot < 0 ? version : version.substring(0, dot));
            } catch (NumberFormatException malformed) {
                return -1;
            }
        }

        /**
         * Returns the key a host knows the query by: its type and major version.
         *
         * @return {@code <queryType>#<major>}
         */
        public String key() {
            return keyOf(queryType, major());
        }

        /**
         * Returns the key of a query type and major version.
         *
         * @param queryType the query type
         * @param major the major version
         * @return {@code <queryType>#<major>}
         */
        public static String keyOf(String queryType, int major) {
            return queryType + "#" + major;
        }
    }

    /**
     * A query the plugin calls.
     *
     * @param queryType the query type
     * @param versionRange the versions it reads
     * @param required whether the plugin cannot be ready without the provider
     * @param timeoutPolicyRef timeout policy reference
     * @param retryPolicyRef retry policy reference
     */
    public record ConsumedQuery(String queryType, String versionRange, boolean required, String timeoutPolicyRef,
            String retryPolicyRef) {

        /**
         * Creates the declaration, blank when not declared.
         */
        public ConsumedQuery {
            queryType = queryType == null ? "" : queryType.trim();
            versionRange = versionRange == null ? "" : versionRange.trim();
            timeoutPolicyRef = timeoutPolicyRef == null ? "" : timeoutPolicyRef.trim();
            retryPolicyRef = retryPolicyRef == null ? "" : retryPolicyRef.trim();
        }
    }

    /**
     * A subscription's policies beyond retry, idempotency and scope.
     *
     * @param timeoutPolicyRef timeout policy reference
     * @param concurrencyPolicyRef concurrency policy reference
     * @param publisherPolicyRef publisher policy reference
     */
    public record SubscriptionPolicies(String timeoutPolicyRef, String concurrencyPolicyRef, String publisherPolicyRef) {

        /**
         * Creates the policies, blank when not declared.
         */
        public SubscriptionPolicies {
            timeoutPolicyRef = timeoutPolicyRef == null ? "" : timeoutPolicyRef.trim();
            concurrencyPolicyRef = concurrencyPolicyRef == null ? "" : concurrencyPolicyRef.trim();
            publisherPolicyRef = publisherPolicyRef == null ? "" : publisherPolicyRef.trim();
        }
    }

    /**
     * Manifest-declared plugin-owned internal contract.
     *
     * @param contractId stable internal contract id
     * @param contractType internal contract Java type
     * @param contractVersion semantic contract version
     * @param providerId stable provider id
     * @param owner owning plugin id
     * @param replayableWrites the write methods whose results the runtime may keep for an operational endpoint's replay
     */
    public record ProvidedInternalContract(
            String contractId,
            String contractType,
            String contractVersion,
            String providerId,
            String owner,
            Set<String> replayableWrites) {

        /**
         * Creates an internal contract declaration.
         */
        public ProvidedInternalContract {
            contractId = requireText(contractId, "contractId");
            contractType = requireText(contractType, "contractType");
            contractVersion = requireText(contractVersion, "contractVersion");
            providerId = requireText(providerId, "providerId");
            owner = requireText(owner, "owner");
            replayableWrites = replayableWrites == null ? Set.of() : Set.copyOf(replayableWrites);
        }
    }
}
