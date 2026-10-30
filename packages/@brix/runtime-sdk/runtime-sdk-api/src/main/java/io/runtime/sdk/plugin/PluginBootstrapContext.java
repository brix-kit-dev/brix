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
package io.runtime.sdk.plugin;

/**
 * Code binding surface for manifest-declared plugin entry points.
 *
 * <p>The YAML manifest declares endpoints, query providers, command handlers,
 * event subscriptions, and managed tasks. A plugin binds the factory of each
 * one during {@link BrixPlugin#configure} (root BRX-RS-MANIFEST-011, MANIFEST-013);
 * the Runtime Shell creates the handlers with the plugin's scoped
 * {@link PluginContext} when the plugin starts (MANIFEST-019), so a handler gets
 * its capabilities from its factory's context and needs no field the plugin
 * fills in {@code onStart}. The Runtime Shell rejects any mismatch between YAML
 * declarations and code bindings.</p>
 *
 * <p>The Runtime Shell's context implements every binding. The factory bindings added in 3.3.0 have defaults that
 * refuse the binding, so that a context written against an earlier SDK, such as a plugin test's recording context,
 * still compiles.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface PluginBootstrapContext {

    /**
     * Binds a manifest-declared endpoint to the factory of its handler.
     *
     * @param handlerId the endpoint's {@code handlerId} (or, when it declares none, its {@code endpointId})
     * @param factory creates the endpoint handler
     * @throws UnsupportedOperationException when this context binds no handler factories
     * @since 3.3.0
     */
    default void bindEndpointHandlerFactory(String handlerId, HandlerFactory<? extends EndpointHandler<?, ?>> factory) {
        throw unsupported("endpoint");
    }

    /**
     * Binds a manifest-declared query provider to the factory of its handler.
     *
     * @param handlerId the query's handler id from the YAML manifest
     * @param factory creates the query handler
     * @throws UnsupportedOperationException when this context binds no handler factories
     * @since 3.3.0
     */
    default void bindQueryHandlerFactory(String handlerId, HandlerFactory<? extends QueryHandler<?, ?>> factory) {
        throw unsupported("query handler");
    }

    /**
     * Binds a manifest-declared command handler to its factory.
     *
     * @param handlerId the command's handler id from the YAML manifest
     * @param factory creates the command handler
     * @throws UnsupportedOperationException when this context binds no handler factories
     * @since 3.3.0
     */
    default void bindCommandHandlerFactory(String handlerId, HandlerFactory<? extends CommandHandler<?>> factory) {
        throw unsupported("command handler");
    }

    /**
     * Binds a manifest-declared event subscription to the factory of its handler (root BRX-RS-CAP-025).
     * The runtime creates the handler with the plugin's scoped context when the plugin starts.
     *
     * @param handlerId the subscription's handler id from the YAML manifest
     * @param factory creates the event handler
     */
    void bindEventHandlerFactory(String handlerId, HandlerFactory<? extends EventHandler<?>> factory);

    /**
     * Binds a manifest-declared managed task to its factory.
     *
     * @param handlerId the task id from the YAML manifest
     * @param factory creates the managed task
     * @throws UnsupportedOperationException when this context binds no handler factories
     * @since 3.3.0
     */
    default void bindTaskFactory(String handlerId, HandlerFactory<? extends ManagedTask> factory) {
        throw unsupported("task");
    }

    /**
     * Binds a manifest-declared endpoint to a handler created before the plugin has a context.
     *
     * @param manifestEndpointId endpoint id from the YAML manifest
     * @param handler endpoint handler
     * @deprecated a handler made in {@code configure} cannot reach its capabilities; use
     *     {@link #bindEndpointHandlerFactory(String, HandlerFactory)}
     */
    @Deprecated(since = "3.3.0", forRemoval = true)
    void bindEndpoint(String manifestEndpointId, EndpointHandler<?, ?> handler);

    /**
     * Binds a manifest-declared query provider to a handler created before the plugin has a context.
     *
     * @param manifestQueryId query id from the YAML manifest
     * @param handler query handler
     * @deprecated use {@link #bindQueryHandlerFactory(String, HandlerFactory)}
     */
    @Deprecated(since = "3.3.0", forRemoval = true)
    void bindQueryHandler(String manifestQueryId, QueryHandler<?, ?> handler);

    /**
     * Binds a manifest-declared command handler created before the plugin has a context.
     *
     * @param manifestCommandId command id from the YAML manifest
     * @param handler command handler
     * @deprecated use {@link #bindCommandHandlerFactory(String, HandlerFactory)}
     */
    @Deprecated(since = "3.3.0", forRemoval = true)
    void bindCommandHandler(String manifestCommandId, CommandHandler<?> handler);

    /**
     * Binds a manifest-declared managed task created before the plugin has a context.
     *
     * @param manifestTaskId task id from the YAML manifest
     * @param task managed task
     * @deprecated use {@link #bindTaskFactory(String, HandlerFactory)}
     */
    @Deprecated(since = "3.3.0", forRemoval = true)
    void bindTask(String manifestTaskId, ManagedTask task);

    private UnsupportedOperationException unsupported(String what) {
        return new UnsupportedOperationException(getClass().getName() + " does not bind " + what + " factories");
    }
}
