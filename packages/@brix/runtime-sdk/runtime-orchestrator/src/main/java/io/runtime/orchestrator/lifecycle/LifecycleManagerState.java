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

/**
 * Lifecycle Manager State.
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public enum LifecycleManagerState {

    /**
     * Created, not yet initialized.
     */
    CREATED("Created"),

    /**
     * Initializing modules.
     */
    INITIALIZING("Initializing"),

    /**
     * All modules initialized.
     */
    INITIALIZED("Initialized"),

    /**
     * Starting modules.
     */
    STARTING("Starting"),

    /**
     * All modules started and running.
     */
    RUNNING("Running"),

    /**
     * Stopping modules.
     */
    STOPPING("Stopping"),

    /**
     * All modules stopped.
     */
    STOPPED("Stopped"),

    /**
     * Error occurred.
     */
    ERROR("Error");

    private final String description;

    LifecycleManagerState(String description) {
        this.description = description;
    }

    /**
     * Gets state description.
     * 
     * @return state description
     */
    public String getDescription() {
        return description;
    }
}
