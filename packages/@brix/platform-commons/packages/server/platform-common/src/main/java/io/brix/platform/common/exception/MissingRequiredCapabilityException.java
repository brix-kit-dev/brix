/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.common.exception;

import java.io.Serial;

/**
 * Thrown when a required Runtime Shell capability is not available at runtime.
 *
 * <p>This exception signals that a capability declared as {@code required} in
 * the host configuration was not registered into the {@code CapabilityRegistry}
 * during application startup, or became unavailable after a dynamic
 * reconfiguration. It maps to HTTP 503 (Service Unavailable) because the
 * platform cannot fulfil the request without the missing capability.</p>
 *
 * <h3>Architecture Position</h3>
 * <ul>
 *   <li>Layer: 2C — Platform Commons (platform-common)</li>
 *   <li>Caught by: {@code GlobalExceptionHandler} in platform-common-starter</li>
 *   <li>Blueprint ref: v3.0.9 §3 Capability Contract, R2.7</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 * @see PlatformErrorCode#CAPABILITY_UNAVAILABLE
 */
public class MissingRequiredCapabilityException extends PlatformException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Fully-qualified type name of the missing capability.
     */
    private final String capabilityName;

    /**
     * Constructs the exception with the missing capability type name.
     *
     * @param capabilityName fully-qualified class name of the missing capability,
     *                       e.g. {@code "io.runtime.sdk.capability.EventBusCapability"}
     */
    public MissingRequiredCapabilityException(String capabilityName) {
        super(PlatformErrorCode.CAPABILITY_UNAVAILABLE,
                "Required capability not available: " + capabilityName);
        this.capabilityName = capabilityName;
    }

    /**
     * Constructs the exception with additional context.
     *
     * @param capabilityName fully-qualified class name of the missing capability
     * @param cause          the underlying exception that prevented registration
     */
    public MissingRequiredCapabilityException(String capabilityName, Throwable cause) {
        super(PlatformErrorCode.CAPABILITY_UNAVAILABLE,
                "Required capability not available: " + capabilityName, cause);
        this.capabilityName = capabilityName;
    }

    /**
     * Returns the fully-qualified type name of the capability that is missing.
     *
     * @return capability class name, never {@code null}
     */
    public String getCapabilityName() {
        return capabilityName;
    }
}
