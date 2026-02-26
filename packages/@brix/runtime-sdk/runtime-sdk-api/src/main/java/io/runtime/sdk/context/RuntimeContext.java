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
package io.runtime.sdk.context;

import java.util.Optional;
import java.util.Set;

import io.runtime.sdk.capability.AuthContextCapability;
import io.runtime.sdk.capability.ConfigStoreCapability;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.LifecycleCapability;
import io.runtime.sdk.capability.LockCapability;
import io.runtime.sdk.capability.ObservabilityCapability;
import io.runtime.sdk.capability.ResilienceCapability;
import io.runtime.sdk.capability.SchedulingCapability;
import io.runtime.sdk.capability.StateStoreCapability;
import io.runtime.sdk.capability.registry.CapabilityDescriptor;
import io.runtime.sdk.capability.registry.CapabilityRegistry;

/**
 * 运行时上下文
 * 
 * <p>运行时上下文是模块获取所有能力的唯一入口点，是运行壳架构的核心接口。
 * 模块在整个生命周期中通过此接口访问平台提供的各种能力。</p>
 * 
 * <h3>核心职责</h3>
 * <ul>
 *   <li>提供核心能力的访问入口</li>
 *   <li>提供可选能力的安全访问</li>
 *   <li>提供上下文信息（租户、模块等）</li>
 * </ul>
 * 
 * <h3>能力分类</h3>
 * <table border="1">
 *   <tr><th>分类</th><th>能力</th><th>说明</th></tr>
 *   <tr><td rowspan="6">核心能力（必须实现）</td><td>EventBus</td><td>事件发布</td></tr>
 *   <tr><td>StateStore</td><td>状态存储</td></tr>
 *   <tr><td>AuthContext</td><td>认证上下文</td></tr>
 *   <tr><td>Observability</td><td>可观测性</td></tr>
 *   <tr><td>ConfigStore</td><td>配置存储</td></tr>
 *   <tr><td>Lifecycle</td><td>生命周期</td></tr>
 *   <tr><td rowspan="3">可选能力</td><td>Scheduling</td><td>定时任务</td></tr>
 *   <tr><td>Lock</td><td>分布式锁</td></tr>
 *   <tr><td>Resilience</td><td>熔断限流</td></tr>
 * </table>
 * 
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>单一入口</b>：所有能力通过 RuntimeContext 获取</li>
 *   <li><b>能力隔离</b>：不同能力独立实现，互不依赖</li>
 *   <li><b>可选能力安全</b>：可选能力返回 Optional，避免空指针</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class BookingModule implements LifecycleCapability {
 *     private RuntimeContext context;
 *     
 *     @Override
 *     public void onInit(RuntimeContext context) {
 *         this.context = context;
 *         
 *         // 使用核心能力
 *         context.getObservability().info("Module initializing...");
 *         
 *         // 使用配置能力
 *         int maxDays = context.getConfigStore().getInt("booking.max-days-ahead", 30);
 *     }
 *     
 *     public void createBooking(BookingCommand command) {
 *         // 使用认证能力
 *         if (!context.getAuthContext().hasPermission("booking:create")) {
 *             throw new AccessDeniedException();
 *         }
 *         
 *         // 使用可选的分布式锁能力
 *         context.getLock().ifPresent(lock -> {
 *             if (lock.tryLock("booking:" + command.getSlotId())) {
 *                 try {
 *                     // 执行预约逻辑...
 *                 } finally {
 *                     lock.unlock("booking:" + command.getSlotId());
 *                 }
 *             }
 *         });
 *         
 *         // 发布事件
 *         context.getEventBus().publish(new BookingCreatedEvent(bookingId));
 *     }
 * }
 * }</pre>
 * 
 * <h3>实现说明</h3>
 * <p>RuntimeContext 由 Host 层实现，不同 Host 提供不同的能力组合：</p>
 * <ul>
 *   <li>Full Product Host：提供所有能力的完整实现</li>
 *   <li>Embedded Host：提供核心能力，可选能力根据客户环境配置</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see EventBusCapability
 * @see StateStoreCapability
 * @see AuthContextCapability
 * @see ObservabilityCapability
 * @see ConfigStoreCapability
 * @see LifecycleCapability
 */
public interface RuntimeContext {

    // ==================== 能力注册表（核心 API） ====================

    /**
     * 获取能力注册表
     * 
     * <p>能力注册表是获取所有能力的统一入口，支持动态能力发现和获取。
     * 这是 v3.0 推荐的能力访问方式。</p>
     * 
     * @return 能力注册表实例
     */
    CapabilityRegistry getCapabilityRegistry();

    /**
     * 获取指定类型的能力实例（通用方法）
     * 
     * <p>通过类型动态获取能力，支持任意已注册的能力类型。
     * 推荐使用此方法而非具体的 getter 方法。</p>
     * 
     * @param capabilityType 能力接口类型
     * @param <T> 能力类型泛型
     * @return 能力实例的 Optional 包装
     */
    default <T> Optional<T> getCapability(Class<T> capabilityType) {
        return getCapabilityRegistry().get(capabilityType);
    }

    /**
     * 获取指定类型的能力实例（必需）
     * 
     * @param capabilityType 能力接口类型
     * @param <T> 能力类型泛型
     * @return 能力实例，不会返回 null
     * @throws io.runtime.sdk.capability.registry.CapabilityNotFoundException 如果能力未注册
     */
    default <T> T getCapabilityRequired(Class<T> capabilityType) {
        return getCapabilityRegistry().getRequired(capabilityType);
    }

    /**
     * 获取所有已注册的能力类型
     * 
     * @return 能力类型集合
     */
    default Set<Class<?>> getAvailableCapabilities() {
        return getCapabilityRegistry().getRegisteredTypes();
    }

    /**
     * 获取能力描述信息
     * 
     * @param capabilityType 能力接口类型
     * @return 能力描述信息
     */
    default Optional<CapabilityDescriptor> getCapabilityDescriptor(Class<?> capabilityType) {
        return getCapabilityRegistry().getDescriptor(capabilityType);
    }

    // ==================== 核心能力快捷方法（向后兼容） ====================

    /**
     * 获取事件总线能力
     * 
     * <p>用于发布领域事件和集成事件。
     * 等价于 {@code getCapabilityRequired(EventBusCapability.class)}。</p>
     * 
     * @return 事件总线能力实例，不会返回 null
     */
    default EventBusCapability getEventBus() {
        return getCapabilityRequired(EventBusCapability.class);
    }

    /**
     * 获取状态存储能力
     * 
     * <p>用于缓存、会话、临时数据存储。
     * 等价于 {@code getCapabilityRequired(StateStoreCapability.class)}。</p>
     * 
     * @return 状态存储能力实例，不会返回 null
     */
    default StateStoreCapability getStateStore() {
        return getCapabilityRequired(StateStoreCapability.class);
    }

    /**
     * 获取认证上下文能力
     * 
     * <p>用于获取当前用户身份和权限。
     * 等价于 {@code getCapabilityRequired(AuthContextCapability.class)}。</p>
     * 
     * @return 认证上下文能力实例，不会返回 null
     */
    default AuthContextCapability getAuthContext() {
        return getCapabilityRequired(AuthContextCapability.class);
    }

    /**
     * 获取可观测性能力
     * 
     * <p>用于日志、指标、追踪。
     * 等价于 {@code getCapabilityRequired(ObservabilityCapability.class)}。</p>
     * 
     * @return 可观测性能力实例，不会返回 null
     */
    default ObservabilityCapability getObservability() {
        return getCapabilityRequired(ObservabilityCapability.class);
    }

    /**
     * 获取配置存储能力
     * 
     * <p>用于读取模块配置。
     * 等价于 {@code getCapabilityRequired(ConfigStoreCapability.class)}。</p>
     * 
     * @return 配置存储能力实例，不会返回 null
     */
    default ConfigStoreCapability getConfigStore() {
        return getCapabilityRequired(ConfigStoreCapability.class);
    }

    /**
     * 获取生命周期能力
     * 
     * <p>用于模块生命周期管理。
     * 等价于 {@code getCapabilityRequired(LifecycleCapability.class)}。</p>
     * 
     * @return 生命周期能力实例，不会返回 null
     */
    default LifecycleCapability getLifecycle() {
        return getCapabilityRequired(LifecycleCapability.class);
    }

    // ==================== 可选能力快捷方法（向后兼容） ====================

    /**
     * 获取定时任务能力（可选）
     * 
     * <p>部分 Host 可能不提供此能力。
     * 等价于 {@code getCapability(SchedulingCapability.class)}。</p>
     * 
     * @return 定时任务能力，如果不可用返回 {@link Optional#empty()}
     */
    default Optional<SchedulingCapability> getScheduling() {
        return getCapability(SchedulingCapability.class);
    }

    /**
     * 获取分布式锁能力（可选）
     * 
     * <p>部分 Host 可能不提供此能力。
     * 等价于 {@code getCapability(LockCapability.class)}。</p>
     * 
     * @return 分布式锁能力，如果不可用返回 {@link Optional#empty()}
     */
    default Optional<LockCapability> getLock() {
        return getCapability(LockCapability.class);
    }

    /**
     * 获取韧性能力（可选）
     * 
     * <p>部分 Host 可能不提供此能力。
     * 等价于 {@code getCapability(ResilienceCapability.class)}。</p>
     * 
     * @return 韧性能力，如果不可用返回 {@link Optional#empty()}
     */
    default Optional<ResilienceCapability> getResilience() {
        return getCapability(ResilienceCapability.class);
    }

    // ==================== 上下文信息 ====================

    /**
     * 获取当前租户 ID
     * 
     * <p>在多租户场景下返回当前请求的租户标识</p>
     * 
     * @return 租户 ID，单租户场景返回默认值或 null
     */
    String getTenantId();

    /**
     * 获取当前模块 ID
     * 
     * <p>返回当前运行上下文所属的模块标识</p>
     * 
     * @return 模块 ID
     */
    String getModuleId();

    /**
     * 检查能力是否可用
     * 
     * @param capabilityType 能力类型
     * @return 如果能力可用返回 true
     */
    default boolean isCapabilityAvailable(Class<?> capabilityType) {
        return getCapabilityRegistry().isAvailable(capabilityType);
    }
}
