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
 * 模块注册表
 * 
 * <p>负责模块的注册、注销和查询。作为运行时所有模块的中央目录，
 * 提供模块发现和元数据管理功能。</p>
 * 
 * <h3>核心职责</h3>
 * <ul>
 *   <li>维护已注册模块的清单</li>
 *   <li>提供模块查询接口</li>
 *   <li>管理模块元数据</li>
 *   <li>检测模块依赖关系</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 注册模块
 * registry.register(bookingModule);
 * 
 * // 查询模块
 * Optional<LifecycleCapability> module = registry.get("shinwa-app-booking");
 * 
 * // 获取所有模块
 * Collection<LifecycleCapability> allModules = registry.getAll();
 * 
 * // 按启动顺序获取模块
 * List<LifecycleCapability> sorted = registry.getByStartupOrder();
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface ModuleRegistry {

    /**
     * 注册模块
     * 
     * <p>将模块添加到注册表中。如果已存在同 ID 模块，将抛出异常。</p>
     * 
     * @param module 要注册的模块
     * @throws IllegalArgumentException 如果 module 为 null
     * @throws ModuleAlreadyRegisteredException 如果模块 ID 已存在
     */
    void register(LifecycleCapability module);

    /**
     * 注销模块
     * 
     * <p>从注册表移除指定模块。如果模块正在运行，应先停止模块。</p>
     * 
     * @param moduleId 模块 ID
     * @return 如果模块存在并被移除返回 true
     */
    boolean unregister(String moduleId);

    /**
     * 获取模块
     * 
     * @param moduleId 模块 ID
     * @return 模块实例，如果不存在返回回 empty
     */
    Optional<LifecycleCapability> get(String moduleId);

    /**
     * 获取模块（必须存在）
     * 
     * @param moduleId 模块 ID
     * @return 模块实例
     * @throws ModuleNotFoundException 如果模块不存在
     */
    LifecycleCapability getRequired(String moduleId);

    /**
     * 获取所有已注册模块
     * 
     * @return 不可变的模块集合
     */
    Collection<LifecycleCapability> getAll();

    /**
     * 按启动顺序获取模块
     * 
     * <p>返回按 startupOrder 排序的模块列表，用于有序启动</p>
     * 
     * @return 排序后的模块列表
     */
    java.util.List<LifecycleCapability> getByStartupOrder();

    /**
     * 按停止顺序获取模块
     * 
     * <p>返回按 startupOrder 倒序排列的模块列表，用于有序停止</p>
     * 
     * @return 排序后的模块列表
     */
    java.util.List<LifecycleCapability> getByShutdownOrder();

    /**
     * 检查模块是否已注册
     * 
     * @param moduleId 模块 ID
     * @return 如果已注册返回 true
     */
    boolean contains(String moduleId);

    /**
     * 获取已注册模块数量
     * 
     * @return 模块数量
     */
    int size();

    /**
     * 清空注册表
     * 
     * <p>警告：此操作会移除所有已注册模块，通常仅用于测试</p>
     */
    void clear();

    /**
     * 获取模块的依赖模块列表
     * 
     * @param moduleId 模块 ID
     * @return 依赖的模块 ID 列表
     */
    java.util.List<String> getDependencies(String moduleId);

    /**
     * 获取依赖指定模块的模块列表
     * 
     * @param moduleId 模块 ID
     * @return 依赖此模块的模块 ID 列表
     */
    java.util.List<String> getDependents(String moduleId);

    /**
     * 验证所有模块依赖是否满足
     * 
     * @return 验证结果
     */
    DependencyValidationResult validateDependencies();

    /**
     * 获取模块元数据
     * 
     * @param moduleId 模块 ID
     * @return 模块元数据
     */
    Optional<ModuleMetadata> getMetadata(String moduleId);

    /**
     * 按依赖关系拓扑排序获取模块
     * 
     * <p>返回按依赖关系排序的模块列表，确保被依赖的模块排在前面。
     * 同时考虑 startupOrder 作为次要排序条件。</p>
     * 
     * <h4>排序规则</h4>
     * <ol>
     *   <li>被依赖的模块优先启动</li>
     *   <li>同级别模块按 startupOrder 升序排列</li>
     * </ol>
     * 
     * @return 拓扑排序后的模块列表
     * @throws CyclicDependencyException 如果存在循环依赖
     */
    java.util.List<LifecycleCapability> getByTopologicalOrder();
}
