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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private final Set<String> queryHandlers;
    private final Set<String> commandHandlers;
    private final Set<String> eventHandlers;
    private final Set<String> tasks;
    private final DataDeclaration data;
    private final Map<String, EventPublication> eventPublications;
    private final Map<String, EventSubscription> eventSubscriptions;
    private final Map<String, ProvidedInternalContract> providedInternalContracts;

    private PluginRuntimeDescriptor(Builder builder) {
        this.identity = Objects.requireNonNull(builder.identity, "identity must not be null");
        this.version = requireText(builder.version, "version");
        this.requiredCapabilities = copy(builder.requiredCapabilities);
        this.optionalCapabilities = copy(builder.optionalCapabilities);
        this.endpoints = Map.copyOf(builder.endpoints);
        this.queryHandlers = copy(builder.queryHandlers);
        this.commandHandlers = copy(builder.commandHandlers);
        this.eventHandlers = copy(builder.eventHandlers);
        this.tasks = copy(builder.tasks);
        this.data = builder.data;
        this.eventPublications = Map.copyOf(builder.eventPublications);
        this.eventSubscriptions = Map.copyOf(builder.eventSubscriptions);
        this.providedInternalContracts = Map.copyOf(builder.providedInternalContracts);
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
     * Returns endpoint declarations keyed by manifest endpoint id.
     *
     * @return immutable endpoint declarations
     */
    public Map<String, EndpointDeclaration> endpointDeclarations() {
        return endpoints;
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
        private final Set<String> queryHandlers = new LinkedHashSet<>();
        private final Set<String> commandHandlers = new LinkedHashSet<>();
        private final Set<String> eventHandlers = new LinkedHashSet<>();
        private final Set<String> tasks = new LinkedHashSet<>();
        private DataDeclaration data = new DataDeclaration("", "", "");
        private final Map<String, EventPublication> eventPublications = new LinkedHashMap<>();
        private final Map<String, EventSubscription> eventSubscriptions = new LinkedHashMap<>();
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
                    endpoint(id, "", "", "");
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
         * @param accessPolicy endpoint access policy
         * @return this builder
         */
        public Builder endpoint(String id, String method, String path, String accessPolicy) {
            if (id != null && !id.isBlank()) {
                endpoints.put(id, new EndpointDeclaration(id, method, path, accessPolicy));
            }
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
            EventSubscription subscription = new EventSubscription(
                subscriptionId,
                eventType,
                schemaRange,
                handlerId,
                retryPolicyRef,
                idempotencyPolicyRef);
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
            if (contractId != null && !contractId.isBlank()) {
                providedInternalContracts.put(contractId, new ProvidedInternalContract(
                    contractId,
                    contractType,
                    contractVersion,
                    providerId,
                    owner));
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
     * @param accessPolicy access policy reference
     */
    public record EndpointDeclaration(String id, String method, String path, String accessPolicy) {

        /**
         * Creates an endpoint declaration.
         */
        public EndpointDeclaration {
            id = id == null ? "" : id.trim();
            method = method == null ? "" : method.trim();
            path = path == null ? "" : path.trim();
            accessPolicy = accessPolicy == null ? "" : accessPolicy.trim();
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
     */
    public record EventSubscription(
            String subscriptionId,
            String eventType,
            String schemaRange,
            String handlerId,
            String retryPolicyRef,
            String idempotencyPolicyRef) {

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
     */
    public record ProvidedInternalContract(
            String contractId,
            String contractType,
            String contractVersion,
            String providerId,
            String owner) {

        /**
         * Creates an internal contract declaration.
         */
        public ProvidedInternalContract {
            contractId = requireText(contractId, "contractId");
            contractType = requireText(contractType, "contractType");
            contractVersion = requireText(contractVersion, "contractVersion");
            providerId = requireText(providerId, "providerId");
            owner = requireText(owner, "owner");
        }
    }
}
