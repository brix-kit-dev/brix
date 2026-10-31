/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.runtime.orchestrator.internalcontract.BrixRange;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.sdk.capability.QueryCapability;
import io.runtime.sdk.query.ContractSchema;
import io.runtime.sdk.query.QueryClient;
import io.runtime.sdk.query.QueryType;

/**
 * The query capability of one plugin: typed clients of the queries it declares under {@code queries.consumes}
 * (root BRX-RS-MESSAGE-009, §4.7).
 *
 * <p>A client is handed out only for a declared query whose types carry the schemas its provider declares, so a
 * factory that asks for anything else stops the plugin's start. The client binds the plugin's identity; the gateway
 * takes everything else from the call the plugin is handling.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
final class PluginQueryCapability implements QueryCapability {

    private final String pluginId;
    private final Map<String, Declared> declared;
    private final QueryGateway gateway;

    PluginQueryCapability(String pluginId, Map<String, Declared> declared, QueryGateway gateway) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId must not be null");
        this.declared = Objects.requireNonNull(declared, "declared must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
    }

    @Override
    public <Q, R> QueryClient<Q, R> client(QueryType<Q, R> type) {
        Objects.requireNonNull(type, "type must not be null");
        Declared declaration = declared.get(type.queryType());
        if (declaration == null) {
            throw new IllegalStateException("Plugin '" + pluginId + "' does not declare query " + type.queryType()
                + " under queries.consumes");
        }
        PluginRuntimeDescriptor.ConsumedQuery consumed = declaration.consumed();
        if (!BrixRange.contains(type.version(), consumed.versionRange())) {
            throw new IllegalStateException("Plugin '" + pluginId + "' holds " + type + ", outside the versions "
                + consumed.versionRange() + " it declares for that query");
        }
        String providerKey = PluginRuntimeDescriptor.ProvidedQuery.keyOf(type.queryType(), type.major());
        if (declaration.provider().isPresent()) {
            PluginRuntimeDescriptor.ProvidedQuery provider = declaration.provider().get();
            if (provider.major() != type.major()) {
                throw new IllegalStateException("Plugin '" + pluginId + "' holds " + type + ", but this host provides "
                    + type.queryType() + " " + provider.version());
            }
            requireSchema(type, type.requestType(), provider.requestSchemaRef(), "request");
            requireSchema(type, type.resultType(), provider.responseSchemaRef(), "result");
            providerKey = provider.key();
        }
        QueryGateway.ConsumerBinding binding = new QueryGateway.ConsumerBinding(pluginId, type.queryType(), providerKey,
            QueryPolicies.timeout(consumed.timeoutPolicyRef(), gateway.settings()),
            QueryPolicies.retriesTransient(consumed.retryPolicyRef()));
        return new Client<>(gateway, binding, type);
    }

    private void requireSchema(QueryType<?, ?> type, Class<?> contractType, String schemaRef, String role) {
        ContractSchema schema = contractType.getAnnotation(ContractSchema.class);
        if (schema == null || !schemaRef.equals(schema.value())) {
            throw new IllegalStateException("Plugin '" + pluginId + "' holds " + type + " whose " + role + " type "
                + contractType.getName() + " carries schema " + (schema == null ? "none" : schema.value())
                + ", but the provider declares " + schemaRef);
        }
    }

    /**
     * A declared query and, when this host provides it, its provider's declaration.
     *
     * @param consumed the consumer's declaration
     * @param provider the provider's declaration, or empty for an optional query no plugin provides
     */
    record Declared(PluginRuntimeDescriptor.ConsumedQuery consumed,
            Optional<PluginRuntimeDescriptor.ProvidedQuery> provider) {
    }

    private record Client<Q, R>(QueryGateway gateway, QueryGateway.ConsumerBinding binding, QueryType<Q, R> type)
            implements QueryClient<Q, R> {

        @Override
        public R execute(Q request) {
            return gateway.execute(binding, type, request);
        }
    }
}
