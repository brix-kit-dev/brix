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
package io.runtime.sdk.capability;

/**
 * HTTP Capability Invocation Exception
 * 
 * <p>Thrown when a {@link HttpCapability} request fails to send.
 * Common causes include network errors, connection timeouts, DNS resolution failures, etc.</p>
 * 
 * <p>Note: HTTP 4xx/5xx responses do not throw this exception. Instead, they return
 * a normal {@link HttpCapability.HttpResult} for the caller to handle based on status code.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.1.0
 */
public class HttpCapabilityException extends RuntimeException {

    public HttpCapabilityException(String message) {
        super(message);
    }

    public HttpCapabilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
