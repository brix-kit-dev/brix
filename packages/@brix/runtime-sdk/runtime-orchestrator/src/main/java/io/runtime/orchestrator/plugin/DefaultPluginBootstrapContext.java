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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.runtime.sdk.plugin.CommandHandler;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.HandlerFactory;
import io.runtime.sdk.plugin.ManagedTask;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.QueryHandler;

/**
 * Manifest-validating implementation of the plugin bootstrap binding context.
 *
 * <p>The context records every code binding made during
 * {@link io.runtime.sdk.plugin.BrixPlugin#configure(PluginBootstrapContext)}
 * and rejects blank, undeclared, duplicate, or null bindings immediately. Full
 * schema-level manifest/code validation is expanded in the manifest migration
 * phase; this Phase 2 implementation already enforces the runtime boundary
 * needed to prevent hidden Spring or Host-side plugin wiring.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class DefaultPluginBootstrapContext implements PluginBootstrapContext {

    private final PluginRuntimeDescriptor descriptor;
    private final Map<String, EndpointHandler<?, ?>> endpoints = new LinkedHashMap<>();
    private final Map<String, QueryHandler<?, ?>> queryHandlers = new LinkedHashMap<>();
    private final Map<String, CommandHandler<?>> commandHandlers = new LinkedHashMap<>();
    private final Map<String, HandlerFactory<? extends EventHandler<?>>> eventHandlerFactories = new LinkedHashMap<>();
    private final Map<String, ManagedTask> tasks = new LinkedHashMap<>();
    private final Map<String, HandlerFactory<? extends EndpointHandler<?, ?>>> endpointHandlerFactories =
        new LinkedHashMap<>();
    private final Map<String, HandlerFactory<? extends QueryHandler<?, ?>>> queryHandlerFactories = new LinkedHashMap<>();
    private final Map<String, HandlerFactory<? extends CommandHandler<?>>> commandHandlerFactories =
        new LinkedHashMap<>();
    private final Map<String, HandlerFactory<? extends ManagedTask>> taskFactories = new LinkedHashMap<>();

    /**
     * Creates a bootstrap context.
     *
     * @param descriptor runtime descriptor resolved from the plugin manifest
     */
    public DefaultPluginBootstrapContext(PluginRuntimeDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bindEndpointHandlerFactory(String handlerId, HandlerFactory<? extends EndpointHandler<?, ?>> factory) {
        String endpointId = descriptor.requireEndpointOfHandler(handlerId);
        if (endpoints.containsKey(endpointId)) {
            throw duplicate("endpoint", endpointId);
        }
        putUnique(endpointHandlerFactories, endpointId, factory, "endpoint");
    }

    @Override
    public void bindQueryHandlerFactory(String handlerId, HandlerFactory<? extends QueryHandler<?, ?>> factory) {
        descriptor.requireQueryHandler(handlerId);
        if (queryHandlers.containsKey(handlerId)) {
            throw duplicate("query handler", handlerId);
        }
        putUnique(queryHandlerFactories, handlerId, factory, "query handler");
    }

    @Override
    public void bindCommandHandlerFactory(String handlerId, HandlerFactory<? extends CommandHandler<?>> factory) {
        descriptor.requireCommandHandler(handlerId);
        if (commandHandlers.containsKey(handlerId)) {
            throw duplicate("command handler", handlerId);
        }
        putUnique(commandHandlerFactories, handlerId, factory, "command handler");
    }

    @Override
    public void bindTaskFactory(String handlerId, HandlerFactory<? extends ManagedTask> factory) {
        descriptor.requireTask(handlerId);
        if (tasks.containsKey(handlerId)) {
            throw duplicate("managed task", handlerId);
        }
        putUnique(taskFactories, handlerId, factory, "managed task");
    }

    @Override
    @Deprecated(since = "3.3.0", forRemoval = true)
    public void bindEndpoint(String manifestEndpointId, EndpointHandler<?, ?> handler) {
        descriptor.requireEndpoint(manifestEndpointId);
        if (endpointHandlerFactories.containsKey(manifestEndpointId)) {
            throw duplicate("endpoint", manifestEndpointId);
        }
        putUnique(endpoints, manifestEndpointId, handler, "endpoint");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated(since = "3.3.0", forRemoval = true)
    public void bindQueryHandler(String manifestQueryId, QueryHandler<?, ?> handler) {
        descriptor.requireQueryHandler(manifestQueryId);
        if (queryHandlerFactories.containsKey(manifestQueryId)) {
            throw duplicate("query handler", manifestQueryId);
        }
        putUnique(queryHandlers, manifestQueryId, handler, "query handler");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated(since = "3.3.0", forRemoval = true)
    public void bindCommandHandler(String manifestCommandId, CommandHandler<?> handler) {
        descriptor.requireCommandHandler(manifestCommandId);
        if (commandHandlerFactories.containsKey(manifestCommandId)) {
            throw duplicate("command handler", manifestCommandId);
        }
        putUnique(commandHandlers, manifestCommandId, handler, "command handler");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bindEventHandlerFactory(String handlerId, HandlerFactory<? extends EventHandler<?>> factory) {
        descriptor.requireEventHandler(handlerId);
        putUnique(eventHandlerFactories, handlerId, factory, "event handler");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated(since = "3.3.0", forRemoval = true)
    public void bindTask(String manifestTaskId, ManagedTask task) {
        descriptor.requireTask(manifestTaskId);
        if (taskFactories.containsKey(manifestTaskId)) {
            throw duplicate("managed task", manifestTaskId);
        }
        putUnique(tasks, manifestTaskId, task, "managed task");
    }

    /**
     * Returns bound endpoints.
     *
     * @return immutable endpoint binding map
     */
    public Map<String, EndpointHandler<?, ?>> endpoints() {
        return Map.copyOf(endpoints);
    }

    /**
     * Returns bound query handlers.
     *
     * @return immutable query handler binding map
     */
    public Map<String, QueryHandler<?, ?>> queryHandlers() {
        return Map.copyOf(queryHandlers);
    }

    /**
     * Returns bound command handlers.
     *
     * @return immutable command handler binding map
     */
    public Map<String, CommandHandler<?>> commandHandlers() {
        return Map.copyOf(commandHandlers);
    }

    /**
     * Returns the factories of the bound event handlers, by handler id.
     *
     * @return immutable event handler factory map
     */
    public Map<String, HandlerFactory<? extends EventHandler<?>>> eventHandlerFactories() {
        return Map.copyOf(eventHandlerFactories);
    }

    /**
     * Returns bound managed tasks.
     *
     * @return immutable managed task binding map
     */
    public Map<String, ManagedTask> tasks() {
        return Map.copyOf(tasks);
    }

    /**
     * Returns the endpoint handler factories, by endpoint id.
     *
     * @return immutable factories
     */
    public Map<String, HandlerFactory<? extends EndpointHandler<?, ?>>> endpointHandlerFactories() {
        return Map.copyOf(endpointHandlerFactories);
    }

    /**
     * Returns the query handler factories, by handler id.
     *
     * @return immutable factories
     */
    public Map<String, HandlerFactory<? extends QueryHandler<?, ?>>> queryHandlerFactories() {
        return Map.copyOf(queryHandlerFactories);
    }

    /**
     * Returns the command handler factories, by handler id.
     *
     * @return immutable factories
     */
    public Map<String, HandlerFactory<? extends CommandHandler<?>>> commandHandlerFactories() {
        return Map.copyOf(commandHandlerFactories);
    }

    /**
     * Returns the managed task factories, by task id.
     *
     * @return immutable factories
     */
    public Map<String, HandlerFactory<? extends ManagedTask>> taskFactories() {
        return Map.copyOf(taskFactories);
    }

    private <T> void putUnique(Map<String, T> target, String id, T binding, String kind) {
        Objects.requireNonNull(binding, kind + " binding must not be null");
        if (target.containsKey(id)) {
            throw duplicate(kind, id);
        }
        target.put(id, binding);
    }

    private PluginRuntimeException duplicate(String kind, String id) {
        return new PluginRuntimeException("Plugin '" + descriptor.identity().pluginId()
            + "' attempted to bind duplicate " + kind + " '" + id + "'");
    }
}
