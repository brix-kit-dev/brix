/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Predicate;

import org.springframework.core.ResolvableType;

import io.runtime.orchestrator.event.EventPolicies;
import io.runtime.orchestrator.internalcontract.BrixRange;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeException;
import io.runtime.sdk.capability.QueryCapability;
import io.runtime.sdk.plugin.QueryHandler;
import io.runtime.sdk.query.ContractSchema;

/**
 * Resolves, binds and publishes the typed queries of a host's plugins (root BRX-RS-MESSAGE-005, BOOT-004, BOOT-005,
 * BOOT-012, LIFE-002, LIFE-011, A-9; query design §4.3, §5.5).
 *
 * <p>The plugin runtime calls it at each stage: {@link #resolve} after the plugins resolve, to check every
 * declaration, find each consumer's one provider and refuse required cycles; {@link #bind} when a provider starts, to
 * check its handlers against its schemas; {@link #capabilityFor} when a plugin asks for its query capability;
 * {@link #publish} and {@link #close} at the Host's entry publication and drain; and
 * {@link #requiredProvidersAvailable} for readiness.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class QueryRuntime {

    private static final tools.jackson.databind.ObjectMapper SCHEMA_READER = new tools.jackson.databind.ObjectMapper();

    private final QueryGateway gateway;
    private final Map<String, Map<String, Provider>> resolution = new LinkedHashMap<>();

    private QueryRuntime(QueryGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * Returns the runtime of a host without query settings: a plugin that provides or calls a query does not start
     * there (root BRX-RS-MESSAGE-005).
     *
     * @return the runtime
     */
    public static QueryRuntime unconfigured() {
        return new QueryRuntime(null);
    }

    /**
     * Returns the runtime of a host whose queries go through {@code gateway}.
     *
     * @param gateway the gateway
     * @return the runtime
     */
    public static QueryRuntime of(QueryGateway gateway) {
        return new QueryRuntime(Objects.requireNonNull(gateway, "gateway must not be null"));
    }

    /**
     * Returns whether the host has query settings, so that plugins may provide and call queries.
     *
     * @return {@code true} with a gateway
     */
    public boolean configured() {
        return gateway != null;
    }

    /**
     * Returns how deeply queries may call queries in this host.
     *
     * @return the depth limit, or 0 without query settings
     */
    public int maxDepth() {
        return gateway == null ? 0 : gateway.settings().maxDepth();
    }

    /**
     * Checks the query declarations of the resolved plugins and resolves each consumer's provider. A plugin whose
     * declarations fail is reported, and the others are resolved again without it, until no new plugin fails (root
     * BRX-RS-BOOT-012).
     *
     * @param plugins the resolved plugins' descriptors, by plugin id
     * @param classLoader the loader that sees the plugins' schemas
     * @return why each failing plugin fails, by plugin id
     */
    public synchronized Map<String, String> resolve(Map<String, PluginRuntimeDescriptor> plugins,
            ClassLoader classLoader) {
        Map<String, String> failures = new LinkedHashMap<>();
        Map<String, PluginRuntimeDescriptor> surviving = new LinkedHashMap<>(plugins);
        while (true) {
            Map<String, String> round = resolveOnce(surviving, classLoader);
            if (round.isEmpty()) {
                return failures;
            }
            round.forEach((pluginId, reason) -> {
                failures.putIfAbsent(pluginId, reason);
                surviving.remove(pluginId);
            });
        }
    }

    private Map<String, String> resolveOnce(Map<String, PluginRuntimeDescriptor> plugins, ClassLoader classLoader) {
        resolution.clear();
        Map<String, String> failures = new LinkedHashMap<>();
        Map<String, List<Provider>> providers = new LinkedHashMap<>();
        for (PluginRuntimeDescriptor descriptor : plugins.values()) {
            String pluginId = descriptor.identity().pluginId();
            boolean declares = !descriptor.providedQueries().isEmpty() || !descriptor.consumedQueries().isEmpty();
            if (declares && gateway == null) {
                failures.put(pluginId, "Plugin '" + pluginId + "' declares queries, but the Host sets no "
                    + "brix.query.max-deadline, the hard cap every query needs (root BRX-RS-MESSAGE-005)");
                continue;
            }
            try {
                for (PluginRuntimeDescriptor.ProvidedQuery query : descriptor.providedQueries().values()) {
                    checkProvided(descriptor, query, classLoader);
                    providers.computeIfAbsent(query.key(), ignored -> new ArrayList<>()).add(new Provider(pluginId, query));
                }
                for (PluginRuntimeDescriptor.ConsumedQuery query : descriptor.consumedQueries().values()) {
                    QueryPolicies.timeout(query.timeoutPolicyRef(), gateway.settings());
                    QueryPolicies.retriesTransient(query.retryPolicyRef());
                }
            } catch (IllegalArgumentException | PluginRuntimeException invalid) {
                failures.put(pluginId, "Plugin '" + pluginId + "' declares an invalid query: " + invalid.getMessage());
            }
        }
        providers.forEach((key, declaring) -> {
            if (declaring.size() > 1) {
                List<String> ids = declaring.stream().map(Provider::pluginId).sorted().toList();
                for (Provider provider : declaring) {
                    failures.putIfAbsent(provider.pluginId(), "Query " + key + " is provided by more than one plugin "
                        + ids + "; a query has one owner");
                }
            }
        });
        Map<String, Set<String>> requiredEdges = new LinkedHashMap<>();
        for (PluginRuntimeDescriptor descriptor : plugins.values()) {
            String pluginId = descriptor.identity().pluginId();
            if (failures.containsKey(pluginId)) {
                continue;
            }
            Map<String, Provider> resolved = new LinkedHashMap<>();
            for (PluginRuntimeDescriptor.ConsumedQuery consumed : descriptor.consumedQueries().values()) {
                List<Provider> matching = providers.values().stream()
                    .filter(candidates -> candidates.size() == 1)
                    .map(candidates -> candidates.get(0))
                    .filter(candidate -> candidate.query().queryType().equals(consumed.queryType()))
                    .filter(candidate -> !failures.containsKey(candidate.pluginId()))
                    .filter(candidate -> BrixRange.contains(candidate.query().version(), consumed.versionRange()))
                    .toList();
                if (matching.size() > 1) {
                    failures.put(pluginId, "Plugin '" + pluginId + "' reads " + consumed.queryType() + " "
                        + consumed.versionRange() + ", which more than one major version on this host matches");
                    break;
                }
                if (matching.isEmpty()) {
                    if (consumed.required()) {
                        failures.put(pluginId, "Plugin '" + pluginId + "' requires query " + consumed.queryType() + " "
                            + consumed.versionRange() + ", which no plugin on this host provides (root BRX-RS-BOOT-004)");
                        break;
                    }
                    continue;
                }
                resolved.put(consumed.queryType(), matching.get(0));
                if (consumed.required()) {
                    requiredEdges.computeIfAbsent(pluginId, ignored -> new LinkedHashSet<>()).add(matching.get(0).pluginId());
                }
            }
            if (!failures.containsKey(pluginId)) {
                resolution.put(pluginId, Map.copyOf(resolved));
            }
        }
        for (List<String> cycle : cycles(requiredEdges)) {
            for (String pluginId : cycle) {
                failures.putIfAbsent(pluginId, "Required queries form a cycle " + String.join(" -> ", cycle) + " -> "
                    + cycle.get(0) + "; required synchronous queries must form a DAG (root BRX-RS-LIFE-011, A-9)");
            }
        }
        failures.keySet().forEach(resolution::remove);
        return failures;
    }

    /** A provided query's policies resolve, its permission is the provider's own, and its schemas exist. */
    private void checkProvided(PluginRuntimeDescriptor descriptor, PluginRuntimeDescriptor.ProvidedQuery query,
            ClassLoader classLoader) {
        if (query.major() < 0) {
            throw new IllegalArgumentException("query " + query.queryType() + " names no versioned request schema");
        }
        QueryPolicies.scope(query.scopePolicyRef());
        QueryPolicies.Access access = QueryPolicies.access(query.accessPolicyRef());
        QueryPolicies.timeout(query.timeoutPolicyRef(), gateway.settings());
        QueryPolicies.concurrency(query.concurrencyPolicyRef(), gateway.settings());
        if (access.kind() != QueryPolicies.Access.Kind.DECLARED_CONSUMERS && descriptor.permissionDeclarations().stream()
                .noneMatch(permission -> permission.id().equals(access.permission()))) {
            throw new IllegalArgumentException("query " + query.queryType() + " requires permission "
                + access.permission() + ", which the plugin does not declare under permissions");
        }
        requireSchema(query.queryType(), query.requestSchemaRef(), classLoader);
        requireSchema(query.queryType(), query.responseSchemaRef(), classLoader);
    }

    private static void requireSchema(String queryType, String schemaRef, ClassLoader classLoader) {
        String resource = EventPolicies.schemaResource(schemaRef);
        if (resource == null) {
            throw new IllegalArgumentException("query " + queryType + " names schema '" + schemaRef
                + "', which is not a brix://schemas/... reference");
        }
        try (InputStream input = classLoader == null ? null : classLoader.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("query " + queryType + " names schema " + schemaRef
                    + ", but no artifact contains " + resource);
            }
            String id = SCHEMA_READER.readTree(input).path("$id").asText("");
            if (!schemaRef.equals(id)) {
                throw new IllegalArgumentException("schema " + resource + " has $id '" + id + "', not " + schemaRef);
            }
        } catch (IOException | RuntimeException unreadable) {
            if (unreadable instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("schema " + resource + " of query " + queryType + " cannot be read: "
                + unreadable.getMessage(), unreadable);
        }
    }

    /** The cycles of the required-query graph, each once. */
    private static List<List<String>> cycles(Map<String, Set<String>> edges) {
        List<List<String>> found = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        for (String start : edges.keySet()) {
            walk(start, edges, new ArrayDeque<>(), new LinkedHashSet<>(), done, found);
        }
        return found;
    }

    private static void walk(String node, Map<String, Set<String>> edges, Deque<String> path, Set<String> onPath,
            Set<String> done, List<List<String>> found) {
        if (done.contains(node)) {
            return;
        }
        if (onPath.contains(node)) {
            List<String> cycle = new ArrayList<>();
            boolean inCycle = false;
            for (String step : path) {
                inCycle = inCycle || step.equals(node);
                if (inCycle) {
                    cycle.add(step);
                }
            }
            found.add(List.copyOf(cycle));
            return;
        }
        path.addLast(node);
        onPath.add(node);
        for (String next : edges.getOrDefault(node, Set.of())) {
            walk(next, edges, path, onPath, done, found);
        }
        onPath.remove(node);
        path.removeLast();
        done.add(node);
    }

    /**
     * Turns a starting provider's query handlers into published entries. Each provided query needs a handler that
     * names its request and result types, and those types must carry the schemas the manifest declares, so the
     * provider and its callers hold one contract (root BRX-RS-MANIFEST-027, A-17, A-22).
     *
     * @param descriptor the provider's descriptor
     * @param handlers the handlers its factories created, by handler id
     * @return the entries, by {@code <queryType>#<major>}
     * @throws PluginRuntimeException when a handler is missing or does not match its declaration
     */
    public Map<String, PublishedQuery> bind(PluginRuntimeDescriptor descriptor, Map<String, QueryHandler<?, ?>> handlers) {
        String pluginId = descriptor.identity().pluginId();
        if (descriptor.providedQueries().isEmpty()) {
            return Map.of();
        }
        if (gateway == null) {
            throw new PluginRuntimeException("Plugin '" + pluginId + "' provides queries, but the Host sets no "
                + "brix.query.max-deadline (root BRX-RS-MESSAGE-005)");
        }
        Map<String, PublishedQuery> published = new LinkedHashMap<>();
        for (PluginRuntimeDescriptor.ProvidedQuery query : descriptor.providedQueries().values()) {
            QueryHandler<?, ?> handler = handlers.get(query.handlerId());
            if (handler == null) {
                throw new PluginRuntimeException("Plugin '" + pluginId + "' binds no handler factory for its provided "
                    + "query " + query.queryType() + " (handler " + query.handlerId() + ")");
            }
            ResolvableType declared = ResolvableType.forClass(handler.getClass()).as(QueryHandler.class);
            Class<?> requestType = declared.getGeneric(0).resolve();
            ResolvableType result = declared.getGeneric(1);
            boolean optional = result.resolve() == Optional.class;
            Class<?> resultType = optional ? result.getGeneric(0).resolve() : result.resolve();
            if (requestType == null || requestType == Object.class || resultType == null || resultType == Object.class) {
                throw new PluginRuntimeException("Query handler '" + query.handlerId() + "' of plugin '" + pluginId
                    + "' must be a class that names its request and result types, QueryHandler<Request, Result> or "
                    + "QueryHandler<Request, Optional<Result>>");
            }
            requireContract(pluginId, query, requestType, query.requestSchemaRef(), "request");
            requireContract(pluginId, query, resultType, query.responseSchemaRef(), "result");
            published.put(query.key(), new PublishedQuery(pluginId, query, handler, requestType, resultType, optional,
                QueryPolicies.scope(query.scopePolicyRef()),
                QueryPolicies.access(query.accessPolicyRef()),
                QueryPolicies.timeout(query.timeoutPolicyRef(), gateway.settings()),
                new Semaphore(QueryPolicies.concurrency(query.concurrencyPolicyRef(), gateway.settings()))));
        }
        return Map.copyOf(published);
    }

    private static void requireContract(String pluginId, PluginRuntimeDescriptor.ProvidedQuery query, Class<?> type,
            String schemaRef, String role) {
        ContractSchema schema = type.getAnnotation(ContractSchema.class);
        if (schema == null || !schemaRef.equals(schema.value())) {
            throw new PluginRuntimeException("The " + role + " type " + type.getName() + " of query "
                + query.queryType() + " in plugin '" + pluginId + "' carries schema "
                + (schema == null ? "none" : schema.value()) + ", but the manifest declares " + schemaRef
                + "; contract types are generated from the declared schemas");
        }
    }

    /**
     * Returns the query capability of a plugin: typed clients of the queries it declares.
     *
     * @param descriptor the plugin's descriptor
     * @return the capability, or empty when the host has no query settings
     */
    public synchronized Optional<QueryCapability> capabilityFor(PluginRuntimeDescriptor descriptor) {
        if (gateway == null) {
            return Optional.empty();
        }
        String pluginId = descriptor.identity().pluginId();
        Map<String, Provider> resolved = resolution.getOrDefault(pluginId, Map.of());
        Map<String, PluginQueryCapability.Declared> declared = new LinkedHashMap<>();
        for (PluginRuntimeDescriptor.ConsumedQuery consumed : descriptor.consumedQueries().values()) {
            Provider provider = resolved.get(consumed.queryType());
            declared.put(consumed.queryType(), new PluginQueryCapability.Declared(consumed,
                provider == null ? Optional.empty() : Optional.of(provider.query())));
        }
        return Optional.of(new PluginQueryCapability(pluginId, Map.copyOf(declared), gateway));
    }

    /**
     * Publishes the queries of one Host entry generation (root A-25).
     *
     * @param queries the started providers' entries
     */
    public void publish(Map<String, PublishedQuery> queries) {
        if (gateway != null) {
            gateway.publish(queries);
        }
    }

    /**
     * Stops admitting query calls before the Host drains (root BRX-RS-LIFE-005).
     */
    public void close() {
        if (gateway != null) {
            gateway.close();
        }
    }

    /**
     * Returns whether every query a plugin requires is published by a ready provider (root BRX-RS-LIFE-002). The
     * state of a circuit breaker does not count: a passing fault does not make plugins not ready (query design §5.5).
     *
     * @param descriptor the consumer's descriptor
     * @param providerReady whether a provider plugin is ready
     * @return {@code true} when the plugin can be ready as far as its queries go
     */
    public synchronized boolean requiredProvidersAvailable(PluginRuntimeDescriptor descriptor,
            Predicate<String> providerReady) {
        Map<String, Provider> resolved = resolution.getOrDefault(descriptor.identity().pluginId(), Map.of());
        for (PluginRuntimeDescriptor.ConsumedQuery consumed : descriptor.consumedQueries().values()) {
            if (!consumed.required()) {
                continue;
            }
            Provider provider = resolved.get(consumed.queryType());
            if (provider == null || gateway == null || !gateway.isPublished(provider.query().key())
                    || !providerReady.test(provider.pluginId())) {
                return false;
            }
        }
        return true;
    }

    private record Provider(String pluginId, PluginRuntimeDescriptor.ProvidedQuery query) {
    }
}
