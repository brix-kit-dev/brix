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
package io.runtime.orchestrator.registry;

import io.runtime.sdk.capability.LifecycleCapability;
import io.runtime.sdk.capability.ModuleMetadata;

import java.util.Collection;
import java.util.Optional;

/**
 * Module Registry.
 * 
 * <p>Responsible for module registration, unregistration, and querying. As the central directory
 * of all modules at runtime, it provides module discovery and metadata management capabilities.</p>
 * 
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>Maintains inventory of registered modules</li>
 *   <li>Provides module query interface</li>
 *   <li>Manages module metadata</li>
 *   <li>Detects module dependencies</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Register module
 * registry.register(bookingModule);
 * 
 * // Query module
 * Optional<LifecycleCapability> module = registry.get("brix-app-booking");
 * 
 * // Get all modules
 * Collection<LifecycleCapability> allModules = registry.getAll();
 * 
 * // Get modules by startup order
 * List<LifecycleCapability> sorted = registry.getByStartupOrder();
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface ModuleRegistry {

    /**
     * Registers a module.
     * 
     * <p>Adds the module to the registry. Throws exception if module with same ID already exists.</p>
     * 
     * @param module the module to register
     * @throws IllegalArgumentException if module is null
     * @throws ModuleAlreadyRegisteredException if module ID already exists
     */
    void register(LifecycleCapability module);

    /**
     * Unregisters a module.
     * 
     * <p>Removes the specified module from the registry. Should stop the module first if it's running.</p>
     * 
     * @param moduleId module ID
     * @return true if module existed and was removed
     */
    boolean unregister(String moduleId);

    /**
     * Gets a module.
     * 
     * @param moduleId module ID
     * @return module instance, returns empty if not exists
     */
    Optional<LifecycleCapability> get(String moduleId);

    /**
     * Gets a module (must exist).
     * 
     * @param moduleId module ID
     * @return module instance
     * @throws ModuleNotFoundException if module does not exist
     */
    LifecycleCapability getRequired(String moduleId);

    /**
     * Gets all registered modules.
     * 
     * @return immutable collection of modules
     */
    Collection<LifecycleCapability> getAll();

    /**
     * Gets modules by startup order.
     * 
     * <p>Returns module list sorted by startupOrder, for ordered startup</p>
     * 
     * @return sorted list of modules
     */
    java.util.List<LifecycleCapability> getByStartupOrder();

    /**
     * Gets modules by shutdown order.
     * 
     * <p>Returns module list in reverse startupOrder, for ordered shutdown</p>
     * 
     * @return sorted list of modules
     */
    java.util.List<LifecycleCapability> getByShutdownOrder();

    /**
     * Checks if a module is registered.
     * 
     * @param moduleId module ID
     * @return true if registered
     */
    boolean contains(String moduleId);

    /**
     * Gets the number of registered modules.
     * 
     * @return module count
     */
    int size();

    /**
     * Clears the registry.
     * 
     * <p>Warning: This operation removes all registered modules, typically only for testing</p>
     */
    void clear();

    /**
     * Gets the dependencies of a module.
     * 
     * @param moduleId module ID
     * @return list of dependency module IDs
     */
    java.util.List<String> getDependencies(String moduleId);

    /**
     * Gets modules that depend on the specified module.
     * 
     * @param moduleId module ID
     * @return list of dependent module IDs
     */
    java.util.List<String> getDependents(String moduleId);

    /**
     * Validates that all module dependencies are satisfied.
     * 
     * @return validation result
     */
    DependencyValidationResult validateDependencies();

    /**
     * Gets module metadata.
     * 
     * @param moduleId module ID
     * @return module metadata
     */
    Optional<ModuleMetadata> getMetadata(String moduleId);

    /**
     * Gets modules in topological order by dependencies.
     * 
     * <p>Returns module list sorted by dependencies, ensuring depended modules come first.
     * Also considers startupOrder as secondary sort criterion.</p>
     * 
     * <h4>Sorting Rules</h4>
     * <ol>
     *   <li>Depended modules start first</li>
     *   <li>Same level modules sorted by startupOrder ascending</li>
     * </ol>
     * 
     * @return topologically sorted list of modules
     * @throws CyclicDependencyException if cyclic dependency exists
     */
    java.util.List<LifecycleCapability> getByTopologicalOrder();
}
