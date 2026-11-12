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
package io.runtime.orchestrator.endpoint;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import tools.jackson.databind.JsonNode;

import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Runtime-owned dispatcher for manifest-declared plugin endpoints.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface PluginEndpointDispatcher {

    /**
     * Atomically replaces the complete Host route snapshot.
     *
     * @param routes manifest-backed routes
     */
    void replaceSnapshot(Collection<EndpointRoute> routes);

    /**
     * Atomically replaces the complete Host route snapshot together with the
     * tenant grant table of the same entry generation.
     *
     * <p>Dispatchers that do not authorize tenant callers ignore the table.</p>
     *
     * @param routes manifest-backed routes
     * @param grants tenant grant table built from the same selected plugins
     * @since 3.2.0
     */
    default void replaceSnapshot(Collection<EndpointRoute> routes, TenantGrantTable grants) {
        replaceSnapshot(routes);
    }

    /**
     * Invokes a published endpoint.
     *
     * @param method request method
     * @param path normalized request path
     * @param body request body
     * @param queryParameters query parameters
     * @param headers request headers
     * @return handler result
     */
    Object invoke(
            String method,
            String path,
            Object body,
            Map<String, List<String>> queryParameters,
            Map<String, List<String>> headers);

    /**
     * Invokes a published endpoint and reports the trace id the runtime gave the call (root BRX-RS-ERROR-013): the
     * caller writes it into the error envelope and its log lines. A dispatcher that makes no trace ids reports none.
     *
     * @param method request method
     * @param path request path as the client sent it
     * @param body request body
     * @param queryParameters query parameters
     * @param headers request headers
     * @param traceIds receives the call's trace id once the route is found
     * @return handler result
     * @since 3.3.0
     */
    default Object invoke(
            String method,
            String path,
            Object body,
            Map<String, List<String>> queryParameters,
            Map<String, List<String>> headers,
            Consumer<String> traceIds) {
        return invoke(method, path, body, queryParameters, headers);
    }

    /**
     * Invokes a published endpoint whose JSON body the caller also read with every decimal digit (root
     * BRX-RS-MANIFEST-022; standard-link plan queue item 7, D123). A typed handler's request is built from
     * {@code exactBody}, so its schema check, its idempotency fingerprint and its {@code B} see the numbers the client
     * wrote; a handler of the old shape gets {@code body}, as it always has. A dispatcher that types no requests ignores
     * {@code exactBody}.
     *
     * @param method request method
     * @param path request path as the client sent it
     * @param body request body as the host's JSON mapper read it
     * @param exactBody reads the same body into a tree that keeps every decimal digit; {@code null} when the request has
     *     no JSON body
     * @param queryParameters query parameters
     * @param headers request headers
     * @param traceIds receives the call's trace id once the route is found
     * @return handler result
     * @since 3.3.0
     */
    default Object invoke(
            String method,
            String path,
            Object body,
            Supplier<JsonNode> exactBody,
            Map<String, List<String>> queryParameters,
            Map<String, List<String>> headers,
            Consumer<String> traceIds) {
        return invoke(method, path, body, queryParameters, headers, traceIds);
    }

    /**
     * Returns the current immutable route snapshot.
     *
     * @return route snapshot
     */
    List<EndpointRoute> routes();

    /**
     * Clears all published routes.
     */
    default void clear() {
        replaceSnapshot(List.of());
    }

    /**
     * No-operation dispatcher for tests that do not exercise endpoints.
     *
     * @return dispatcher
     */
    static PluginEndpointDispatcher none() {
        return new PluginEndpointDispatcher() {
            @Override
            public void replaceSnapshot(Collection<EndpointRoute> routes) {
            }

            @Override
            public Object invoke(
                    String method,
                    String path,
                    Object body,
                    Map<String, List<String>> queryParameters,
                    Map<String, List<String>> headers) {
                throw new EndpointDispatchException("No Runtime Shell endpoint dispatcher is configured");
            }

            @Override
            public List<EndpointRoute> routes() {
                return List.of();
            }
        };
    }
}
