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
 * event subscriptions, and managed tasks. A plugin uses this context to bind
 * the matching implementation callbacks during {@link BrixPlugin#configure}.
 * The Runtime Shell is responsible for rejecting any mismatch between YAML
 * declarations and code bindings.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface PluginBootstrapContext {

    /**
     * Binds a manifest-declared endpoint.
     *
     * @param manifestEndpointId endpoint id from the YAML manifest
     * @param handler endpoint handler
     */
    void bindEndpoint(String manifestEndpointId, EndpointHandler<?, ?> handler);

    /**
     * Binds a manifest-declared query provider.
     *
     * @param manifestQueryId query id from the YAML manifest
     * @param handler query handler
     */
    void bindQueryHandler(String manifestQueryId, QueryHandler<?, ?> handler);

    /**
     * Binds a manifest-declared command handler.
     *
     * @param manifestCommandId command id from the YAML manifest
     * @param handler command handler
     */
    void bindCommandHandler(String manifestCommandId, CommandHandler<?, ?> handler);

    /**
     * Binds a manifest-declared event subscription.
     *
     * @param manifestSubscriptionId subscription id from the YAML manifest
     * @param handler event handler
     */
    void bindEventHandler(String manifestSubscriptionId, EventHandler<?> handler);

    /**
     * Binds a manifest-declared managed task.
     *
     * @param manifestTaskId task id from the YAML manifest
     * @param task managed task
     */
    void bindTask(String manifestTaskId, ManagedTask task);
}
