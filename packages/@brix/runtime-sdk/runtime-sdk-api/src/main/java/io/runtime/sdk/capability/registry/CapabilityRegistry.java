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

import java.util.Optional;
import java.util.Set;

/**
 * 能力注册表接口
 * 
 * <p>提供运行时能力的动态注册与获取能力，是 Runtime Shell 的核心抽象。
 * 通过注册表模式，实现能力的声明式组装，避免硬编码依赖。</p>
 * 
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>类型安全</b>：通过泛型确保类型安全的能力获取</li>
 *   <li><b>声明式</b>：能力通过配置声明，而非代码硬编码</li>
 *   <li><b>可扩展</b>：新能力无需修改核心代码，只需注册</li>
 *   <li><b>可观测</b>：提供能力元数据查询能力</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 获取必需能力（不存在则抛异常）
 * EventBusCapability eventBus = registry.getRequired(EventBusCapability.class);
 * 
 * // 获取可选能力
 * registry.get(LockCapability.class).ifPresent(lock -> {
 *     lock.tryLock("resource-key", Duration.ofSeconds(10));
 * });
 * 
 * // 检查能力是否可用
 * if (registry.isAvailable(SchedulingCapability.class)) {
 *     // 使用调度能力
 * }
 * }</pre>
 * 
 * <h3>业界参考</h3>
 * <ul>
 *   <li>OSGi BundleContext - 服务注册与发现</li>
 *   <li>Kubernetes API Server - 资源注册</li>
 *   <li>VS Code Extension API - Capability Provider</li>
 *   <li>Eclipse RCP - Service Registry</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see Capability
 * @see CapabilityDescriptor
 */
public interface CapabilityRegistry {

    // ==================== 能力获取 ====================

    /**
     * 获取指定类型的能力实例（可选）
     * 
     * <p>推荐用于可选能力的获取，调用方需要处理能力不存在的情况。</p>
     * 
     * @param capabilityType 能力接口类型
     * @param <T> 能力类型泛型
     * @return 能力实例的 Optional 包装，如果未注册返回 empty
     */
    <T> Optional<T> get(Class<T> capabilityType);

    /**
     * 获取指定类型的能力实例（必需）
     * 
     * <p>用于核心能力的获取，如果能力未注册则抛出异常。</p>
     * 
     * @param capabilityType 能力接口类型
     * @param <T> 能力类型泛型
     * @return 能力实例，不会返回 null
     * @throws CapabilityNotFoundException 如果能力未注册
     */
    <T> T getRequired(Class<T> capabilityType);

    /**
     * 获取指定类型的能力实例，如果不存在则返回默认值
     * 
     * @param capabilityType 能力接口类型
     * @param defaultValue 默认值
     * @param <T> 能力类型泛型
     * @return 能力实例或默认值
     */
    default <T> T getOrDefault(Class<T> capabilityType, T defaultValue) {
        return get(capabilityType).orElse(defaultValue);
    }

    // ==================== 能力检查 ====================

    /**
     * 检查能力是否可用
     * 
     * @param capabilityType 能力接口类型
     * @return 如果能力已注册且可用返回 true
     */
    boolean isAvailable(Class<?> capabilityType);

    /**
     * 获取所有已注册的能力类型
     * 
     * @return 能力类型集合（不可修改）
     */
    Set<Class<?>> getRegisteredTypes();

    /**
     * 获取能力描述信息
     * 
     * @param capabilityType 能力接口类型
     * @return 能力描述信息，如果未注册返回 empty
     */
    Optional<CapabilityDescriptor> getDescriptor(Class<?> capabilityType);

    /**
     * 获取所有能力描述信息
     * 
     * @return 所有能力描述信息集合
     */
    Set<CapabilityDescriptor> getAllDescriptors();

    // ==================== 能力注册 ====================

    /**
     * 注册能力实例
     * 
     * @param capabilityType 能力接口类型
     * @param instance 能力实例
     * @param <T> 能力类型泛型
     * @throws IllegalStateException 如果注册表已冻结
     */
    <T> void register(Class<T> capabilityType, T instance);

    /**
     * 注册能力实例（带描述信息）
     * 
     * @param capabilityType 能力接口类型
     * @param instance 能力实例
     * @param descriptor 能力描述信息
     * @param <T> 能力类型泛型
     */
    <T> void register(Class<T> capabilityType, T instance, CapabilityDescriptor descriptor);

    /**
     * 条件注册能力实例
     * 
     * <p>只有当该类型未注册时才进行注册</p>
     * 
     * @param capabilityType 能力接口类型
     * @param instance 能力实例
     * @param <T> 能力类型泛型
     * @return 是否成功注册（false 表示已存在）
     */
    <T> boolean registerIfAbsent(Class<T> capabilityType, T instance);

    // ==================== 生命周期 ====================

    /**
     * 冻结注册表
     * 
     * <p>冻结后不允许再注册新能力，用于确保运行时稳定性。
     * 通常在应用启动完成后调用。</p>
     */
    void freeze();

    /**
     * 检查注册表是否已冻结
     * 
     * @return 如果已冻结返回 true
     */
    boolean isFrozen();

    /**
     * 验证必需能力是否已注册
     * 
     * @param requiredTypes 必需的能力类型数组
     * @throws CapabilityNotFoundException 如果有必需能力未注册
     */
    void validateRequired(Class<?>... requiredTypes);

    /**
     * 获取已注册能力数量
     * 
     * @return 能力数量
     */
    int size();
}
