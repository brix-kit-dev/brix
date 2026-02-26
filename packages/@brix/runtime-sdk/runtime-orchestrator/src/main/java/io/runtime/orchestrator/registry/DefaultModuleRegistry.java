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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认模块注册表实现
 * 
 * <p>线程安全的模块注册表实现，基于 ConcurrentHashMap 存储模块。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class DefaultModuleRegistry implements ModuleRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DefaultModuleRegistry.class);

    /**
     * 模块存储 - 模块ID -> 模块实例
     */
    private final Map<String, LifecycleCapability> modules = new ConcurrentHashMap<>();

    /**
     * 元数据缓存 - 模块ID -> 元数据
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
            
            // 检查缺失依赖
            for (String dependency : dependsOn) {
                if (!modules.containsKey(dependency)) {
                    missingDependencies.add(moduleId + " -> " + dependency);
                }
            }
        }

        // 简单的循环依赖检测
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
     * 检测循环依赖
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
     * <p>使用 Kahn 算法实现拓扑排序，按依赖关系和 startupOrder 确定启动顺序。</p>
     */
    @Override
    public List<LifecycleCapability> getByTopologicalOrder() {
        // 如果没有模块，直接返回空列表
        if (modules.isEmpty()) {
            return Collections.emptyList();
        }

        // 计算每个模块的入度（被依赖次数）
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> graph = new HashMap<>();  // 依赖图：A -> [B, C] 表示 A 依赖 B 和 C

        // 初始化
        for (String moduleId : modules.keySet()) {
            inDegree.put(moduleId, 0);
            graph.put(moduleId, new ArrayList<>());
        }

        // 构建依赖图并计算入度
        for (String moduleId : modules.keySet()) {
            List<String> deps = getDependencies(moduleId);
            for (String dep : deps) {
                if (modules.containsKey(dep)) {
                    // dep -> moduleId：dep 被 moduleId 依赖，所以 moduleId 的入度增加
                    graph.get(dep).add(moduleId);
                    inDegree.put(moduleId, inDegree.get(moduleId) + 1);
                }
            }
        }

        // 使用优先队列，按 startupOrder 排序
        PriorityQueue<String> queue = new PriorityQueue<>((a, b) -> {
            int orderA = metadataCache.get(a) != null ? metadataCache.get(a).getStartupOrder() : 0;
            int orderB = metadataCache.get(b) != null ? metadataCache.get(b).getStartupOrder() : 0;
            return Integer.compare(orderA, orderB);
        });

        // 将入度为 0 的模块加入队列（没有依赖的模块可以首先启动）
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        // 拓扑排序
        List<LifecycleCapability> result = new ArrayList<>();
        List<String> sortedIds = new ArrayList<>();

        while (!queue.isEmpty()) {
            String moduleId = queue.poll();
            sortedIds.add(moduleId);
            result.add(modules.get(moduleId));

            // 移除该模块的边，更新邻居的入度
            for (String neighbor : graph.get(moduleId)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        // 检查是否所有模块都已排序（如果不是，说明存在循环依赖）
        if (result.size() != modules.size()) {
            // 找出循环依赖的模块
            List<String> cyclicModules = new ArrayList<>();
            for (String moduleId : modules.keySet()) {
                if (!sortedIds.contains(moduleId)) {
                    cyclicModules.add(moduleId);
                }
            }
            
            // 尝试找出具体的循环路径
            List<String> cyclePath = findCyclePath(cyclicModules.get(0), new LinkedHashSet<>());
            throw new CyclicDependencyException(cyclePath);
        }

        logger.debug("拓扑排序完成，启动顺序: {}", sortedIds);
        return result;
    }

    /**
     * 查找循环依赖路径
     * 
     * @param startModuleId 起始模块 ID
     * @param path          当前路径
     * @return 循环路径
     */
    private List<String> findCyclePath(String startModuleId, LinkedHashSet<String> path) {
        if (path.contains(startModuleId)) {
            // 找到循环，构建循环路径
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
            cyclePath.add(startModuleId);  // 闭合循环
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
