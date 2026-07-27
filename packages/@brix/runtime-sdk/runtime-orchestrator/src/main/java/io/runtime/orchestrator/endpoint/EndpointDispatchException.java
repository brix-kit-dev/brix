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

/**
 * Raised when Runtime Shell cannot dispatch a manifest endpoint invocation.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public class EndpointDispatchException extends RuntimeException {

    /**
     * Creates a dispatch exception.
     *
     * @param message error message
     */
    public EndpointDispatchException(String message) {
        super(message);
    }

    /**
     * Creates a dispatch exception.
     *
     * @param message error message
     * @param cause root cause
     */
    public EndpointDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
