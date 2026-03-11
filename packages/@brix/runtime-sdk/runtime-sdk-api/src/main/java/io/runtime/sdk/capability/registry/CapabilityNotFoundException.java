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
package io.runtime.sdk.capability.registry;

/**
 * Capability Not Found Exception
 * 
 * <p>Thrown when a required but unregistered capability is requested.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class CapabilityNotFoundException extends RuntimeException {

    private final Class<?> capabilityType;

    /**
     * Constructor
     * 
     * @param capabilityType capability type
     */
    public CapabilityNotFoundException(Class<?> capabilityType) {
        super("Required capability not found: " + capabilityType.getName());
        this.capabilityType = capabilityType;
    }

    /**
     * Constructor
     * 
     * @param capabilityType capability type
     * @param message error message
     */
    public CapabilityNotFoundException(Class<?> capabilityType, String message) {
        super(message);
        this.capabilityType = capabilityType;
    }

    /**
     * Get capability type
     * 
     * @return capability type
     */
    public Class<?> getCapabilityType() {
        return capabilityType;
    }
}
