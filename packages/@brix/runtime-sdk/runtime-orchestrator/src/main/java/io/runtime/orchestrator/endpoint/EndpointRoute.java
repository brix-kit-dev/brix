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

import java.util.Locale;
import java.util.Objects;

import io.runtime.sdk.plugin.EndpointHandler;

/**
 * Runtime-owned route entry created from a manifest declaration and handler
 * binding.
 *
 * @param pluginId owner plugin id
 * @param endpointId manifest endpoint id
 * @param method normalized HTTP method
 * @param path manifest route template
 * @param accessPolicy manifest access policy reference
 * @param handler bound endpoint handler
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record EndpointRoute(
        String pluginId,
        String endpointId,
        String method,
        String path,
        String accessPolicy,
        EndpointHandler<?, ?> handler) {

    /**
     * Creates a route entry.
     */
    public EndpointRoute {
        pluginId = requireText(pluginId, "pluginId");
        endpointId = requireText(endpointId, "endpointId");
        method = requireText(method, "method").toUpperCase(Locale.ROOT);
        path = normalizePath(path);
        accessPolicy = accessPolicy == null ? "" : accessPolicy.trim();
        handler = Objects.requireNonNull(handler, "handler must not be null");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizePath(String value) {
        String pathValue = requireText(value, "path");
        if (!pathValue.startsWith("/")) {
            pathValue = "/" + pathValue;
        }
        while (pathValue.length() > 1 && pathValue.endsWith("/")) {
            pathValue = pathValue.substring(0, pathValue.length() - 1);
        }
        return pathValue;
    }
}
