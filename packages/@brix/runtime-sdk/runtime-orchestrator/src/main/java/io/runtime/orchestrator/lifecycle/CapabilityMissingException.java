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
package io.runtime.orchestrator.lifecycle;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Capability Missing Exception.
 * 
 * <p>Thrown when a required capability declared by a module does not exist in the Host.
 * This exception causes module startup failure, indicating a serious error that requires
 * checking Host configuration or module dependency declarations.</p>
 * 
 * <h3>Triggering Scenarios</h3>
 * <ul>
 *   <li>Module declares need for event-bus capability, but Host doesn't provide EventBusCapability implementation</li>
 *   <li>Module declares need for scheduling capability (in required), but Host doesn't provide SchedulingCapability implementation</li>
 * </ul>
 * 
 * <h3>Resolution Suggestions</h3>
 * <ul>
 *   <li>Check if Host correctly registered implementations for all required capabilities</li>
 *   <li>Check if capabilities.required configuration in module's module-manifest.yaml is correct</li>
 *   <li>If capability is not required, consider moving it to capabilities.optional</li>
 * </ul>
 * 
 * <h3>Example</h3>
 * <pre>{@code
 * // Module manifest declares need for scheduling capability
 * capabilities:
 *   required:
 *     - scheduling  # If Host doesn't provide, this exception will be thrown
 *     
 * // Catch exception
 * try {
 *     lifecycleManager.initialize(moduleId);
 * } catch (CapabilityMissingException e) {
 *     logger.error("Module {} startup failed: missing capability {}", 
 *         e.getModuleId(), e.getMissingCapabilities());
 *     // Log audit...
 * }
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ModuleLifecycleManager
 */
public class CapabilityMissingException extends ModuleLifecycleException {

    private static final long serialVersionUID = 1L;

    /**
     * Set of missing capabilities.
     */
    private final Set<String> missingCapabilities;

    /**
     * Creates capability missing exception (single capability).
     * 
     * @param moduleId   module ID
     * @param capability missing capability identifier
     */
    public CapabilityMissingException(String moduleId, String capability) {
        this(moduleId, Collections.singleton(capability));
    }

    /**
     * Creates capability missing exception (multiple capabilities).
     * 
     * @param moduleId     module ID
     * @param capabilities set of missing capabilities
     */
    public CapabilityMissingException(String moduleId, Collection<String> capabilities) {
        super(moduleId, LifecyclePhase.INIT, buildMessage(moduleId, capabilities));
        this.missingCapabilities = Set.copyOf(capabilities);
    }

    /**
     * Creates capability missing exception (with cause).
     * 
     * @param moduleId     module ID
     * @param capabilities set of missing capabilities
     * @param cause        cause exception
     */
    public CapabilityMissingException(String moduleId, Collection<String> capabilities, Throwable cause) {
        super(moduleId, LifecyclePhase.INIT, cause);
        this.missingCapabilities = Set.copyOf(capabilities);
    }

    /**
     * Gets set of missing capabilities.
     * 
     * @return immutable set of missing capabilities
     */
    public Set<String> getMissingCapabilities() {
        return missingCapabilities;
    }

    /**
     * Checks if specified capability is missing.
     * 
     * @param capability capability identifier
     * @return true if that capability is missing
     */
    public boolean isMissing(String capability) {
        return missingCapabilities.contains(capability);
    }

    /**
     * Builds error message.
     */
    private static String buildMessage(String moduleId, Collection<String> capabilities) {
        return String.format(
            "Module [%s] requires the following capabilities but Host did not provide: %s. " +
            "Please check Host configuration or move these capabilities to capabilities.optional.",
            moduleId, capabilities
        );
    }
}
