/*
 * Copyright 2026 Brix Authors
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
package io.runtime.orchestrator.capability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityDescriptor;
import io.runtime.sdk.capability.registry.CapabilityNotFoundException;
import io.runtime.sdk.capability.registry.CapabilityRegistry;

/**
 * 默认能力注册表实现
 *
 * <p>实现 {@link CapabilityRegistry} 接口，提供多 Host 模式共享的通用能力注册与查找逻辑。</p>
 *
 * <h2>架构定位（v3.0 运行壳架构蓝图）</h2>
 * <p>
 * 本类属于 <b>runtime-orchestrator</b>（编排层），实现 runtime-sdk-api（契约层）定义的
 * {@link CapabilityRegistry} 接口。蓝图明确要求契约层（Layer 2）仅包含纯接口定义，
 * 而实现类应位于编排层或能力实现层。
 * </p>
 * <p>
 * 本类从原 host-shell-standalone 的 {@code StandaloneCapabilityRegistry} 演化而来，
 * 作为通用默认实现由各 Host（Standalone/Embedded）复用或扩展，
 * 消除了两种 Host 模式之间的重复代码。
 * </p>
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li><b>类型安全注册</b>：基于 {@code Class<T>} 作为 key 的泛型注册与获取</li>
 *   <li><b>能力描述符</b>：通过 {@link CapabilityDescriptor} 管理能力元数据</li>
 *   <li><b>别名支持</b>：一个能力可以通过多个别名查找</li>
 *   <li><b>冻结机制</b>：启动完成后冻结注册表，防止运行时篡改</li>
 *   <li><b>必需能力验证</b>：启动时校验所有必需能力是否已注册</li>
 *   <li><b>注解驱动注册</b>：支持从 {@link Capability @Capability} 注解自动注册</li>
 * </ul>
 *
 * <h2>线程安全</h2>
 * <p>
 * 所有内部存储基于 {@link ConcurrentHashMap}，注册阶段支持并发写入。
 * 冻结后进入只读模式，所有写操作抛出 {@link IllegalStateException}。
 * </p>
 *
 * <h2>使用方式</h2>
 * <pre>{@code
 * // 由 Host 层的 AutoConfiguration 创建并管理
 * DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
 *
 * // 注册能力
 * registry.register(EventBusCapability.class, kafkaEventBus);
 * registry.register(StateStoreCapability.class, redisStateStore);
 *
 * // 从注解注册
 * registry.registerFromAnnotation(fallbackAuthContext);
 *
 * // 验证并冻结
 * registry.validateRequired(EventBusCapability.class, StateStoreCapability.class);
 * registry.freeze();
 *
 * // 获取能力
 * EventBusCapability eventBus = registry.getRequired(EventBusCapability.class);
 * }</pre>
 *
 * @author Brix Platform Authors
 * @since 3.0.0
 * @see CapabilityRegistry
 * @see CapabilityDescriptor
 */
public class DefaultCapabilityRegistry implements CapabilityRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultCapabilityRegistry.class);

    /** 能力实例存储：接口类型 -> 实现实例 */
    private final Map<Class<?>, Object> capabilities = new ConcurrentHashMap<>();

    /** 能力描述符存储：接口类型 -> 描述符（包含名称、级别、别名等元数据） */
    private final Map<Class<?>, CapabilityDescriptor> descriptors = new ConcurrentHashMap<>();

    /** 能力别名映射：别名字符串 -> 主接口类型（允许通过别名查找能力） */
    private final Map<String, Class<?>> aliases = new ConcurrentHashMap<>();

    /** 注册表冻结标志，冻结后禁止任何写操作 */
    private volatile boolean frozen = false;

    public DefaultCapabilityRegistry() {
        log.debug("创建 DefaultCapabilityRegistry 实例");
    }

    // ==================== CapabilityRegistry 接口实现 ====================

    /**
     * 获取指定类型的能力实例（可选）
     *
     * @param capabilityType 能力接口类型
     * @param <T> 能力类型参数
     * @return 能力实例的 Optional 包装，未注册时返回 empty
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(Class<T> capabilityType) {
        Objects.requireNonNull(capabilityType, "能力类型不能为空");
        return Optional.ofNullable((T) capabilities.get(capabilityType));
    }

    /**
     * 获取必需的能力实例
     *
     * <p>当能力未注册时抛出 {@link CapabilityNotFoundException}，
     * 错误信息包含已注册的能力列表，便于排查配置问题。</p>
     *
     * @param capabilityType 能力接口类型
     * @param <T> 能力类型参数
     * @return 能力实例
     * @throws CapabilityNotFoundException 当能力未注册时
     */
    @Override
    public <T> T getRequired(Class<T> capabilityType) {
        return get(capabilityType).orElseThrow(() ->
            new CapabilityNotFoundException(capabilityType,
                "必需的能力未注册: " + capabilityType.getSimpleName()
                + "。已注册的能力: " + getRegisteredTypes().stream()
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(", ")))
        );
    }

    /**
     * 检查指定能力是否已注册
     *
     * @param capabilityType 能力接口类型
     * @return 已注册返回 true
     */
    @Override
    public boolean isAvailable(Class<?> capabilityType) {
        return capabilities.containsKey(capabilityType);
    }

    /**
     * 获取所有已注册的能力类型集合
     *
     * @return 不可变的类型集合
     */
    @Override
    public Set<Class<?>> getRegisteredTypes() {
        return Collections.unmodifiableSet(capabilities.keySet());
    }

    /**
     * 获取指定能力的描述符
     *
     * @param capabilityType 能力接口类型
     * @return 描述符的 Optional 包装
     */
    @Override
    public Optional<CapabilityDescriptor> getDescriptor(Class<?> capabilityType) {
        return Optional.ofNullable(descriptors.get(capabilityType));
    }

    /**
     * 获取所有能力描述符集合
     *
     * @return 不可变的描述符集合
     */
    @Override
    public Set<CapabilityDescriptor> getAllDescriptors() {
        return Collections.unmodifiableSet(new HashSet<>(descriptors.values()));
    }

    /**
     * 注册能力实例（自动创建默认描述符）
     *
     * <p>如果实例带有 {@link Capability @Capability} 注解，描述符从注解推断；
     * 否则创建默认描述符。</p>
     *
     * @param capabilityType 能力接口类型
     * @param instance 能力实现实例
     * @param <T> 能力类型参数
     * @throws IllegalStateException 注册表已冻结时
     */
    @Override
    public <T> void register(Class<T> capabilityType, T instance) {
        register(capabilityType, instance, createDefaultDescriptor(capabilityType, instance));
    }

    /**
     * 注册能力实例（带描述符）
     *
     * <p>如果相同类型已注册，新实例会覆盖旧实例并输出警告日志。
     * 描述符中的别名会自动注册到别名映射表。</p>
     *
     * @param capabilityType 能力接口类型
     * @param instance 能力实现实例
     * @param descriptor 能力描述符
     * @param <T> 能力类型参数
     * @throws IllegalStateException 注册表已冻结时
     */
    @Override
    public <T> void register(Class<T> capabilityType, T instance, CapabilityDescriptor descriptor) {
        Objects.requireNonNull(capabilityType, "能力类型不能为空");
        Objects.requireNonNull(instance, "能力实例不能为空");

        checkNotFrozen();

        if (capabilities.containsKey(capabilityType)) {
            log.warn("覆盖已注册的能力: {} -> {}", capabilityType.getSimpleName(),
                    instance.getClass().getSimpleName());
        }

        capabilities.put(capabilityType, instance);

        if (descriptor != null) {
            descriptors.put(capabilityType, descriptor);
            // 注册别名映射，允许通过别名查找能力
            for (String alias : descriptor.getAliases()) {
                aliases.put(alias, capabilityType);
                log.debug("注册能力别名: {} -> {}", alias, capabilityType.getSimpleName());
            }
        }

        log.info("注册能力: {} -> {} ({})",
                capabilityType.getSimpleName(),
                instance.getClass().getSimpleName(),
                descriptor != null ? descriptor.getLevel() : "DEFAULT");
    }

    /**
     * 仅在能力未注册时注册
     *
     * <p>用于注册兜底实现，避免覆盖已有的正式实现。</p>
     *
     * @param capabilityType 能力接口类型
     * @param instance 能力实现实例
     * @param <T> 能力类型参数
     * @return 注册成功返回 true，已存在则返回 false
     */
    @Override
    public <T> boolean registerIfAbsent(Class<T> capabilityType, T instance) {
        if (capabilities.containsKey(capabilityType)) {
            log.debug("跳过注册（已存在）: {}", capabilityType.getSimpleName());
            return false;
        }
        register(capabilityType, instance);
        return true;
    }

    /**
     * 冻结注册表
     *
     * <p>冻结后所有写操作（register、clear）将抛出 {@link IllegalStateException}。
     * 通常在 Spring 容器启动完成后调用，防止运行时意外修改能力映射。</p>
     */
    @Override
    public void freeze() {
        this.frozen = true;
        log.info("能力注册表已冻结，共注册 {} 个能力: {}",
                capabilities.size(),
                capabilities.keySet().stream()
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(", ")));
    }

    /**
     * 检查注册表是否已冻结
     *
     * @return 已冻结返回 true
     */
    @Override
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * 验证必需能力是否全部已注册
     *
     * <p>在冻结前调用，确保所有核心能力都已就位。
     * 任何缺失的能力会导致 {@link CapabilityNotFoundException}。</p>
     *
     * @param requiredTypes 必需的能力类型列表
     * @throws CapabilityNotFoundException 当有必需能力缺失时
     */
    @Override
    public void validateRequired(Class<?>... requiredTypes) {
        List<String> missing = new ArrayList<>();

        for (Class<?> type : requiredTypes) {
            if (!isAvailable(type)) {
                missing.add(type.getSimpleName());
            }
        }

        if (!missing.isEmpty()) {
            throw new CapabilityNotFoundException(requiredTypes[0],
                "以下必需能力未注册: " + String.join(", ", missing));
        }

        log.debug("必需能力验证通过: {} 个", requiredTypes.length);
    }

    /**
     * 获取已注册能力数量
     *
     * @return 能力数量
     */
    @Override
    public int size() {
        return capabilities.size();
    }

    // ==================== 扩展方法 ====================

    /**
     * 通过别名获取能力实例
     *
     * <p>别名在注册时通过 {@link CapabilityDescriptor#getAliases()} 自动建立映射。
     * 适用于需要通过字符串标识符查找能力的场景。</p>
     *
     * @param alias 能力别名
     * @param <T> 能力类型参数
     * @return 能力实例的 Optional 包装
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getByAlias(String alias) {
        Class<?> type = aliases.get(alias);
        if (type == null) {
            return Optional.empty();
        }
        return (Optional<T>) get(type);
    }

    /**
     * 从带有 {@link Capability @Capability} 注解的实例自动注册
     *
     * <p>从注解中推断能力类型和描述符信息，简化注册过程。
     * 与 Spring 的自动发现机制配合使用。</p>
     *
     * @param instance 带有 @Capability 注解的实例
     * @throws IllegalArgumentException 实例没有 @Capability 注解时
     */
    public void registerFromAnnotation(Object instance) {
        Class<?> clazz = instance.getClass();
        Capability annotation = clazz.getAnnotation(Capability.class);

        if (annotation == null) {
            throw new IllegalArgumentException("实例没有 @Capability 注解: " + clazz.getName());
        }

        CapabilityDescriptor descriptor = CapabilityDescriptor.fromAnnotation(annotation, clazz);

        @SuppressWarnings("unchecked")
        Class<Object> capabilityType = (Class<Object>) descriptor.getType();
        register(capabilityType, instance, descriptor);
    }

    /**
     * 清理所有注册的能力（仅用于测试）
     *
     * @throws IllegalStateException 注册表已冻结时
     */
    public void clear() {
        checkNotFrozen();
        capabilities.clear();
        descriptors.clear();
        aliases.clear();
        log.debug("能力注册表已清空");
    }

    // ==================== 内部方法 ====================

    /**
     * 为能力实例创建默认描述符
     *
     * <p>优先从 @Capability 注解推断，无注解时创建最小描述符。</p>
     */
    private <T> CapabilityDescriptor createDefaultDescriptor(Class<T> type, T instance) {
        Capability annotation = instance.getClass().getAnnotation(Capability.class);
        if (annotation != null) {
            return CapabilityDescriptor.fromAnnotation(annotation, instance.getClass());
        }

        return CapabilityDescriptor.builder(type)
                .name(type.getSimpleName())
                .implementationClass(instance.getClass().getName())
                .build();
    }

    /**
     * 检查注册表是否已冻结，冻结后禁止写操作
     *
     * @throws IllegalStateException 注册表已冻结时
     */
    private void checkNotFrozen() {
        if (frozen) {
            throw new IllegalStateException("能力注册表已冻结，不允许修改");
        }
    }

    @Override
    public String toString() {
        return "DefaultCapabilityRegistry{"
                + "frozen=" + frozen
                + ", capabilities=" + capabilities.keySet().stream()
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(", ", "[", "]"))
                + '}';
    }
}
