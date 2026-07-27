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
 * Framework-neutral exception for expected endpoint handling failures.
 *
 * <p>Runtime protocol adapters translate this exception into their native
 * response shape. Plugins use stable error codes and status values without
 * importing protocol framework types.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public class EndpointHandlingException extends RuntimeException {

    private final int status;
    private final String errorCode;

    /**
     * Creates an endpoint handling exception.
     *
     * @param status protocol status code
     * @param errorCode stable error code
     * @param message safe error message
     */
    public EndpointHandlingException(int status, String errorCode, String message) {
        super(message);
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("status must be a 4xx or 5xx protocol status");
        }
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        this.status = status;
        this.errorCode = errorCode;
    }

    /**
     * Returns the protocol status code.
     *
     * @return status code
     */
    public int status() {
        return status;
    }

    /**
     * Returns the stable error code.
     *
     * @return error code
     */
    public String errorCode() {
        return errorCode;
    }
}
