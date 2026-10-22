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

import io.runtime.sdk.plugin.EndpointHandlingException;

/**
 * Raised by the Runtime Shell Dispatcher when an invocation fails the declared
 * endpoint access policy. It is always thrown before the endpoint handler runs.
 *
 * @author Runtime SDK Team
 * @since 3.2.0
 */
public final class EndpointAccessDeniedException extends EndpointHandlingException {

    /** No Runtime-verified principal for an endpoint that requires one. */
    public static final String AUTHENTICATION_REQUIRED = "ENDPOINT_AUTHENTICATION_REQUIRED";
    /** The verified token class is not accepted by the endpoint. */
    public static final String TOKEN_NOT_ACCEPTED = "ENDPOINT_TOKEN_NOT_ACCEPTED";
    /** The endpoint forbids a tenant context but the principal carries one. */
    public static final String TENANT_CONTEXT_FORBIDDEN = "ENDPOINT_TENANT_CONTEXT_FORBIDDEN";
    /** The endpoint requires a tenant context but the principal carries none. */
    public static final String TENANT_CONTEXT_REQUIRED = "ENDPOINT_TENANT_CONTEXT_REQUIRED";
    /** The verified principal lacks the declared permissions. */
    public static final String PERMISSION_DENIED = "ENDPOINT_PERMISSION_DENIED";

    private final String endpointId;

    /**
     * Creates an access denial.
     *
     * @param status protocol status code
     * @param errorCode stable error code
     * @param endpointId denied endpoint id
     * @param message safe error message
     */
    public EndpointAccessDeniedException(int status, String errorCode, String endpointId, String message) {
        super(status, errorCode, message);
        this.endpointId = endpointId;
    }

    /**
     * Returns the denied endpoint id.
     *
     * @return endpoint id
     */
    public String endpointId() {
        return endpointId;
    }
}
