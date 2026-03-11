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

import io.runtime.manifest.model.ModuleManifest;
import io.runtime.sdk.capability.HealthStatus;
import io.runtime.sdk.context.RuntimeContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Module Lifecycle Manager.
 * 
 * <p>Responsible for managing the lifecycle of all modules, including initialization, startup,
 * health check and shutdown. Supports ordered startup (by dependency and startupOrder)
 * and graceful shutdown.</p>
 * 
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>Initialize and start modules in dependency order</li>
 *   <li>Execute periodic health checks</li>
 *   <li>Stop modules in reverse order (graceful shutdown)</li>
 *   <li>Handle module lifecycle events</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Create manager
 * ModuleLifecycleManager manager = new DefaultModuleLifecycleManager(registry, contextFactory);
 * 
 * // Initialize all modules
 * manager.initializeAll().join();
 * 
 * // Start all modules
 * manager.startAll().join();
 * 
 * // Get health status
 * Map<String, HealthStatus> health = manager.checkHealth();
 * 
 * // Graceful shutdown
 * manager.stopAll().join();
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface ModuleLifecycleManager {

    /**
     * Initializes all registered modules.
     * 
     * <p>Initializes modules in startupOrder and dependency order</p>
     * 
     * @return CompletableFuture indicating initialization completion
     */
    CompletableFuture<Void> initializeAll();

    /**
     * Initializes specified module.
     * 
     * @param moduleId module ID
     * @return CompletableFuture indicating initialization completion
     */
    CompletableFuture<Void> initialize(String moduleId);

    /**
     * Starts all initialized modules.
     * 
     * <p>Starts modules in startupOrder and dependency order</p>
     * 
     * @return CompletableFuture indicating startup completion
     */
    CompletableFuture<Void> startAll();

    /**
     * Starts specified module.
     * 
     * @param moduleId module ID
     * @return CompletableFuture indicating startup completion
     */
    CompletableFuture<Void> start(String moduleId);

    /**
     * Stops all running modules.
     * 
     * <p>Stops modules in reverse startupOrder, ensuring dependencies stop first</p>
     * 
     * @return CompletableFuture indicating stop completion
     */
    CompletableFuture<Void> stopAll();

    /**
     * Stops specified module.
     * 
     * @param moduleId module ID
     * @return CompletableFuture indicating stop completion
     */
    CompletableFuture<Void> stop(String moduleId);

    /**
     * Destroys all modules.
     * 
     * <p>Releases all module resources</p>
     * 
     * @return CompletableFuture indicating destroy completion
     */
    CompletableFuture<Void> destroyAll();

    /**
     * Checks health status of all modules.
     * 
     * @return Map of moduleId -> health status
     */
    Map<String, HealthStatus> checkHealth();

    /**
     * Checks health status of specified module.
     * 
     * @param moduleId module ID
     * @return health status
     */
    HealthStatus checkHealth(String moduleId);

    /**
     * Restarts specified module.
     * 
     * @param moduleId module ID
     * @return CompletableFuture indicating restart completion
     */
    CompletableFuture<Void> restart(String moduleId);

    /**
     * Sets runtime context factory.
     * 
     * @param contextFactory context factory
     */
    void setContextFactory(RuntimeContextFactory contextFactory);

    /**
     * Adds lifecycle listener.
     * 
     * @param listener lifecycle listener
     */
    void addListener(LifecycleListener listener);

    /**
     * Removes lifecycle listener.
     * 
     * @param listener lifecycle listener
     */
    void removeListener(LifecycleListener listener);

    /**
     * Gets manager state.
     * 
     * @return manager current state
     */
    LifecycleManagerState getState();

    /**
     * Sets capability provider.
     * 
     * <p>Capability provider is used to verify whether required capabilities are available for modules</p>
     * 
     * @param capabilityProvider capability provider
     */
    void setCapabilityProvider(CapabilityProvider capabilityProvider);

    /**
     * Validates module's capability dependencies.
     * 
     * <p>Checks whether all required capabilities declared in module manifest are provided by Host.
     * If there are missing required capabilities, throws {@link CapabilityMissingException}.</p>
     * 
     * @param manifest module manifest
     * @throws CapabilityMissingException if required capability is missing
     */
    void validateCapabilities(ModuleManifest manifest);

    /**
     * Runtime context factory.
     * 
     * <p>Used to create runtime context for each module</p>
     */
    @FunctionalInterface
    interface RuntimeContextFactory {
        /**
         * Creates runtime context for specified module.
         * 
         * @param moduleId module ID
         * @return runtime context
         */
        RuntimeContext createContext(String moduleId);
    }

    /**
     * Capability provider interface.
     * 
     * <p>Used to query capabilities provided by Host</p>
     */
    interface CapabilityProvider {
        
        /**
         * Checks if specified capability is provided.
         * 
         * @param capability capability identifier (e.g., "event-bus", "state-store")
         * @return true if that capability is provided
         */
        boolean hasCapability(String capability);
        
        /**
         * Gets all provided capabilities.
         * 
         * @return set of capability identifiers
         */
        Set<String> getCapabilities();
    }
}
