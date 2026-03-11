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
 * Lifecycle Event Listener.
 * 
 * <p>Listens to module lifecycle change events.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface LifecycleListener {

    /**
     * Called before module initialization.
     * 
     * @param moduleId module ID
     */
    default void beforeInit(String moduleId) {}

    /**
     * Called after module initialization.
     * 
     * @param moduleId module ID
     * @param success whether successful
     */
    default void afterInit(String moduleId, boolean success) {}

    /**
     * Called before module start.
     * 
     * @param moduleId module ID
     */
    default void beforeStart(String moduleId) {}

    /**
     * Called after module start.
     * 
     * @param moduleId module ID
     * @param success whether successful
     */
    default void afterStart(String moduleId, boolean success) {}

    /**
     * Called before module stop.
     * 
     * @param moduleId module ID
     */
    default void beforeStop(String moduleId) {}

    /**
     * Called after module stop.
     * 
     * @param moduleId module ID
     */
    default void afterStop(String moduleId) {}

    /**
     * Called before module destroy.
     * 
     * @param moduleId module ID
     */
    default void beforeDestroy(String moduleId) {}

    /**
     * Called after module destroy.
     * 
     * @param moduleId module ID
     */
    default void afterDestroy(String moduleId) {}

    /**
     * Called when module error occurs.
     * 
     * @param moduleId module ID
     * @param phase lifecycle phase
     * @param error error information
     */
    default void onError(String moduleId, LifecyclePhase phase, Throwable error) {}
}
