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

import java.util.Objects;

/**
 * Framework-neutral endpoint response returned by plugin handlers.
 *
 * <p>Handlers use this contract when they need to control the protocol status
 * without importing HTTP framework types. Runtime adapters translate it at the
 * protocol boundary.</p>
 *
 * @param status protocol status code
 * @param body response body, or {@code null}
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record EndpointResponse(int status, Object body) {

    /**
     * Creates a response.
     */
    public EndpointResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("status must be a valid protocol status");
        }
    }

    /**
     * Creates a 200 response.
     *
     * @param body response body
     * @return endpoint response
     */
    public static EndpointResponse ok(Object body) {
        return new EndpointResponse(200, body);
    }

    /**
     * Creates a 201 response.
     *
     * @param body response body
     * @return endpoint response
     */
    public static EndpointResponse created(Object body) {
        return new EndpointResponse(201, body);
    }

    /**
     * Returns this response body converted by the caller.
     *
     * @return response body
     */
    @Override
    public Object body() {
        return body;
    }

    /**
     * Verifies a non-null response body and returns it.
     *
     * @return non-null response body
     */
    public Object requireBody() {
        return Objects.requireNonNull(body, "body must not be null");
    }
}
