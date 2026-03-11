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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.LifecycleCapability;
import io.runtime.sdk.capability.ModuleMetadata;

/**
 * Default Module Registry Implementation.
 * 
 * <p>Thread-safe module registry implementation based on ConcurrentHashMap storage.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class DefaultModuleRegistry implements ModuleRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DefaultModuleRegistry.class);

    /**
     * Module storage - moduleId -> module instance.
     */
    private final Map<String, LifecycleCapability> modules = new ConcurrentHashMap<>();

    /**
     * Metadata cache - moduleId -> metadata.
     */
    private final Map<String, ModuleMetadata> metadataCache = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void register(LifecycleCapability module) {
        Objects.requireNonNull(module, "Module cannot be null");
        
        ModuleMetadata metadata = module.getMetadata();
        Objects.requireNonNull(metadata, "Module metadata cannot be null");
        
        String moduleId = metadata.getModuleId();
        Objects.requireNonNull(moduleId, "Module ID cannot be null");
        
        if (modules.containsKey(moduleId)) {
            throw new ModuleAlreadyRegisteredException(moduleId);
        }
        
        modules.put(moduleId, module);
        metadataCache.put(moduleId, metadata);
        
        logger.info("Module registered: {} ({})", moduleId, metadata.getModuleName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean unregister(String moduleId) {
        Objects.requireNonNull(moduleId, "Module ID cannot be null");
        
        LifecycleCapability removed = modules.remove(moduleId);
        metadataCache.remove(moduleId);
        
        if (removed != null) {
            logger.info("Module unregistered: {}", moduleId);
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<LifecycleCapability> get(String moduleId) {
        return Optional.ofNullable(modules.get(moduleId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LifecycleCapability getRequired(String moduleId) {
        return get(moduleId)
            .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<LifecycleCapability> getAll() {
        return Collections.unmodifiableCollection(modules.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LifecycleCapability> getByStartupOrder() {
        return modules.values().stream()
            .sorted(Comparator.comparingInt(m -> m.getMetadata().getStartupOrder()))
            .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LifecycleCapability> getByShutdownOrder() {
        return modules.values().stream()
            .sorted(Comparator.comparingInt(m -> -m.getMetadata().getStartupOrder()))
            .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(String moduleId) {
        return modules.containsKey(moduleId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return modules.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        modules.clear();
        metadataCache.clear();
        logger.warn("Module registry cleared");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getDependencies(String moduleId) {
        return getMetadata(moduleId)
            .map(meta -> Arrays.asList(meta.getDependsOn()))
            .orElse(Collections.emptyList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getDependents(String moduleId) {
        return metadataCache.values().stream()
            .filter(meta -> Arrays.asList(meta.getDependsOn()).contains(moduleId))
            .map(ModuleMetadata::getModuleId)
            .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DependencyValidationResult validateDependencies() {
        List<String> missingDependencies = new ArrayList<>();
        List<String> circularDependencies = new ArrayList<>();

        for (Map.Entry<String, ModuleMetadata> entry : metadataCache.entrySet()) {
            String moduleId = entry.getKey();
            String[] dependsOn = entry.getValue().getDependsOn();
            
            // Check missing dependencies
            for (String dependency : dependsOn) {
                if (!modules.containsKey(dependency)) {
                    missingDependencies.add(moduleId + " -> " + dependency);
                }
            }
        }

        // Simple circular dependency detection
        for (String moduleId : modules.keySet()) {
            Set<String> visited = new HashSet<>();
            if (hasCircularDependency(moduleId, visited, new HashSet<>())) {
                circularDependencies.add(moduleId);
            }
        }

        return new DependencyValidationResult(missingDependencies, circularDependencies);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ModuleMetadata> getMetadata(String moduleId) {
        return Optional.ofNullable(metadataCache.get(moduleId));
    }

    /**
     * Detects circular dependencies using DFS traversal.
     *
     * @param moduleId the current module ID being checked
     * @param visited set of already visited modules
     * @param path current traversal path for cycle detection
     * @return true if circular dependency is detected, false otherwise
     */
    private boolean hasCircularDependency(String moduleId, Set<String> visited, Set<String> path) {
        if (path.contains(moduleId)) {
            return true;
        }
        if (visited.contains(moduleId)) {
            return false;
        }

        visited.add(moduleId);
        path.add(moduleId);

        List<String> deps = getDependencies(moduleId);
        for (String dep : deps) {
            if (hasCircularDependency(dep, visited, path)) {
                return true;
            }
        }

        path.remove(moduleId);
        return false;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Uses Kahn's algorithm for topological sorting, determining startup order
     * based on dependency relationships and startupOrder configuration.</p>
     */
    @Override
    public List<LifecycleCapability> getByTopologicalOrder() {
        // Return empty list if no modules are registered
        if (modules.isEmpty()) {
            return Collections.emptyList();
        }

        // Calculate in-degree for each module (number of dependencies)
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> graph = new HashMap<>();  // Dependency graph: A -> [B, C] means B and C depend on A

        // Initialize in-degree and graph structures
        for (String moduleId : modules.keySet()) {
            inDegree.put(moduleId, 0);
            graph.put(moduleId, new ArrayList<>());
        }

        // Build dependency graph and calculate in-degrees
        for (String moduleId : modules.keySet()) {
            List<String> deps = getDependencies(moduleId);
            for (String dep : deps) {
                if (modules.containsKey(dep)) {
                    // dep -> moduleId: moduleId depends on dep, so moduleId's in-degree increases
                    graph.get(dep).add(moduleId);
                    inDegree.put(moduleId, inDegree.get(moduleId) + 1);
                }
            }
        }

        // Use priority queue for ordering by startupOrder
        PriorityQueue<String> queue = new PriorityQueue<>((a, b) -> {
            int orderA = metadataCache.get(a) != null ? metadataCache.get(a).getStartupOrder() : 0;
            int orderB = metadataCache.get(b) != null ? metadataCache.get(b).getStartupOrder() : 0;
            return Integer.compare(orderA, orderB);
        });

        // Enqueue modules with zero in-degree (modules with no dependencies can start first)
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        // Topological sort using Kahn's algorithm
        List<LifecycleCapability> result = new ArrayList<>();
        List<String> sortedIds = new ArrayList<>();

        while (!queue.isEmpty()) {
            String moduleId = queue.poll();
            sortedIds.add(moduleId);
            result.add(modules.get(moduleId));

            // Remove edges and update neighbors' in-degrees
            for (String neighbor : graph.get(moduleId)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        // Verify all modules were sorted (if not, circular dependency exists)
        if (result.size() != modules.size()) {
            // Identify modules involved in circular dependencies
            List<String> cyclicModules = new ArrayList<>();
            for (String moduleId : modules.keySet()) {
                if (!sortedIds.contains(moduleId)) {
                    cyclicModules.add(moduleId);
                }
            }
            
            // Attempt to find the specific cycle path
            List<String> cyclePath = findCyclePath(cyclicModules.get(0), new LinkedHashSet<>());
            throw new CyclicDependencyException(cyclePath);
        }

        logger.debug("Topological sort completed, startup order: {}", sortedIds);
        return result;
    }

    /**
     * Finds the path of circular dependency.
     * 
     * <p>Uses DFS to trace the dependency chain and identify the exact cycle path.</p>
     *
     * @param startModuleId starting module ID for search
     * @param path current traversal path (ordered set to preserve order)
     * @return list representing the cycle path, empty if no cycle found
     */
    private List<String> findCyclePath(String startModuleId, LinkedHashSet<String> path) {
        if (path.contains(startModuleId)) {
            // Cycle found, build cycle path
            List<String> cyclePath = new ArrayList<>();
            boolean foundStart = false;
            for (String id : path) {
                if (id.equals(startModuleId)) {
                    foundStart = true;
                }
                if (foundStart) {
                    cyclePath.add(id);
                }
            }
            cyclePath.add(startModuleId);  // Close the cycle
            return cyclePath;
        }

        path.add(startModuleId);
        
        for (String dep : getDependencies(startModuleId)) {
            if (modules.containsKey(dep)) {
                List<String> result = findCyclePath(dep, path);
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        
        path.remove(startModuleId);
        return Collections.emptyList();
    }
}
