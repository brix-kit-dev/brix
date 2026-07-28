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
    private final Map<String, EventHandler<?>> eventHandlers = new LinkedHashMap<>();
    private final Map<String, ManagedTask> tasks = new LinkedHashMap<>();

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
    public void bindEndpoint(String manifestEndpointId, EndpointHandler<?, ?> handler) {
        descriptor.requireEndpoint(manifestEndpointId);
        putUnique(endpoints, manifestEndpointId, handler, "endpoint");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bindQueryHandler(String manifestQueryId, QueryHandler<?, ?> handler) {
        descriptor.requireQueryHandler(manifestQueryId);
        putUnique(queryHandlers, manifestQueryId, handler, "query handler");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bindCommandHandler(String manifestCommandId, CommandHandler<?> handler) {
        descriptor.requireCommandHandler(manifestCommandId);
        putUnique(commandHandlers, manifestCommandId, handler, "command handler");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bindEventHandler(String manifestSubscriptionId, EventHandler<?> handler) {
        descriptor.requireEventHandler(manifestSubscriptionId);
        putUnique(eventHandlers, manifestSubscriptionId, handler, "event handler");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bindTask(String manifestTaskId, ManagedTask task) {
        descriptor.requireTask(manifestTaskId);
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
     * Returns bound event handlers.
     *
     * @return immutable event handler binding map
     */
    public Map<String, EventHandler<?>> eventHandlers() {
        return Map.copyOf(eventHandlers);
    }

    /**
     * Returns bound managed tasks.
     *
     * @return immutable managed task binding map
     */
    public Map<String, ManagedTask> tasks() {
        return Map.copyOf(tasks);
    }

    private <T> void putUnique(Map<String, T> target, String id, T binding, String kind) {
        Objects.requireNonNull(binding, kind + " binding must not be null");
        if (target.containsKey(id)) {
            throw new PluginRuntimeException("Plugin '" + descriptor.identity().pluginId()
                + "' attempted to bind duplicate " + kind + " '" + id + "'");
        }
        target.put(id, binding);
    }
}
