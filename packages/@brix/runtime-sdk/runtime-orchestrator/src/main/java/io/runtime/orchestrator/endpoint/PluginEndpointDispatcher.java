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
