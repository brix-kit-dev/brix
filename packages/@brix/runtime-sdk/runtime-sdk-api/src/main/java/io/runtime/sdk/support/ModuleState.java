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
package io.runtime.sdk.support;

/**
 * Module State Enumeration
 * 
 * <p>Defines various states of a module throughout its lifecycle.</p>
 * 
 * <h3>State Transitions</h3>
 * <pre>{@code
 * REGISTERED -> INITIALIZING -> INITIALIZED -> STARTING -> RUNNING -> STOPPING -> STOPPED -> DESTROYED
 *                    |                             |                       |
 *                    v                             v                       |
 *                 FAILED                       DEGRADED <-----------------+
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public enum ModuleState {

    /**
     * Registered state
     * 
     * <p>Module class has been discovered and registered but not yet initialized</p>
     */
    REGISTERED("Registered"),

    /**
     * Initializing state
     * 
     * <p>Module is executing onInit() method</p>
     */
    INITIALIZING("Initializing"),

    /**
     * Initialized state
     * 
     * <p>Module onInit() completed, waiting to start</p>
     */
    INITIALIZED("Initialized"),

    /**
     * Starting state
     * 
     * <p>Module is executing onStart() method</p>
     */
    STARTING("Starting"),

    /**
     * Running state
     * 
     * <p>Module is running normally and can handle requests</p>
     */
    RUNNING("Running"),

    /**
     * Degraded state
     * 
     * <p>Some module functionality is unavailable but core functions are normal</p>
     */
    DEGRADED("Degraded"),

    /**
     * Stopping state
     * 
     * <p>Module is executing onStop() method</p>
     */
    STOPPING("Stopping"),

    /**
     * Stopped state
     * 
     * <p>Module has stopped and no longer handles requests</p>
     */
    STOPPED("Stopped"),

    /**
     * Destroyed state
     * 
     * <p>Module has been destroyed and resources have been released</p>
     */
    DESTROYED("Destroyed"),

    /**
     * Failed state
     * 
     * <p>Module initialization or startup failed</p>
     */
    FAILED("Failed");

    /**
     * State description
     */
    private final String description;

    ModuleState(String description) {
        this.description = description;
    }

    /**
     * Get state description
     * 
     * @return state description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Check if module is available (can handle requests)
     * 
     * @return true if module is available
     */
    public boolean isAvailable() {
        return this == RUNNING || this == DEGRADED;
    }

    /**
     * Check if module is in terminal state
     * 
     * @return true if module has terminated
     */
    public boolean isTerminal() {
        return this == STOPPED || this == DESTROYED || this == FAILED;
    }
}
