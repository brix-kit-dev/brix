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
package io.runtime.orchestrator.context;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.StateStoreCapability;

/**
 * 租户隔离的状态存储装饰器
 * 
 * <p>自动为 Key 添加租户前缀，确保多租户环境下的数据隔离。
 * 这是实现多租户状态存储隔离的核心组件。</p>
 * 
 * <h2>隔离策略</h2>
 * <p>所有存储键都会自动添加租户前缀：</p>
 * <pre>
 * 原始 Key: "session:user123"
 * 隔离 Key: "tenant:tenant001:session:user123"
 * </pre>
 * 
 * <h2>设计原则</h2>
 * <ul>
 *   <li><b>透明隔离</b>：业务代码无需感知租户前缀</li>
 *   <li><b>装饰器模式</b>：不修改原有 StateStore 实现</li>
 *   <li><b>运行时绑定</b>：从 TenantContext 动态获取租户ID</li>
 * </ul>
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 创建租户隔离的状态存储
 * StateStoreCapability delegate = // 原始实现（Redis/Memory）
 * StateStoreCapability isolated = new TenantIsolatedStateStore(delegate, "tenant001");
 * 
 * // 存储数据 - 实际 Key: "tenant:tenant001:user:123"
 * isolated.put("user:123", userData, Duration.ofHours(1));
 * 
 * // 读取数据 - 只能读到当前租户的数据
 * Optional<UserData> data = isolated.get("user:123", UserData.class);
 * }</pre>
 * 
 * <h2>动态租户绑定</h2>
 * <pre>{@code
 * // 从 TenantContext 动态获取租户
 * StateStoreCapability isolated = TenantIsolatedStateStore.wrap(delegate);
 * 
 * // 设置租户上下文
 * TenantContext.set("tenant002");
 * 
 * // 存储时自动使用当前租户
 * isolated.put("key", value, ttl);  // Key: "tenant:tenant002:key"
 * }</pre>
 * 
 * <h2>架构归属</h2>
 * <p>本类属于 <b>编排层（Orchestrator）</b>，是对 StateStoreCapability 的运行时装饰。
 * 从 runtime-sdk-api 迁移至此，因为租户隔离逻辑属于运行时编排职责，
 * 而非基础 Capability 接口契约。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see StateStoreCapability
 * @see TenantContext
 */
public class TenantIsolatedStateStore implements StateStoreCapability {

    private static final Logger log = LoggerFactory.getLogger(TenantIsolatedStateStore.class);

    /**
     * 租户键前缀格式
     * 
     * <p>格式为 "tenant:{tenantId}:"，与原始 Key 拼接后构成隔离键。</p>
     */
    private static final String TENANT_KEY_PREFIX = "tenant:%s:";

    /**
     * 委托的状态存储实例
     */
    private final StateStoreCapability delegate;

    /**
     * 固定租户ID（可选，如果为 null 则从 TenantContext 动态获取）
     */
    private final String fixedTenantId;

    /**
     * 创建租户隔离的状态存储（固定租户）
     * 
     * @param delegate 委托的状态存储实例
     * @param tenantId 固定的租户ID
     */
    public TenantIsolatedStateStore(StateStoreCapability delegate, String tenantId) {
        this.delegate = Objects.requireNonNull(delegate, "delegate 不能为空");
        this.fixedTenantId = Objects.requireNonNull(tenantId, "tenantId 不能为空");
        log.debug("创建租户隔离状态存储: tenantId={}", tenantId);
    }

    /**
     * 创建租户隔离的状态存储（动态租户）
     * 
     * <p>运行时从 TenantContext 获取当前租户ID。</p>
     * 
     * @param delegate 委托的状态存储实例
     */
    private TenantIsolatedStateStore(StateStoreCapability delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate 不能为空");
        this.fixedTenantId = null;
        log.debug("创建动态租户隔离状态存储");
    }

    /**
     * 包装状态存储为动态租户隔离版本
     * 
     * <p>返回的实例会在运行时从 TenantContext 获取租户ID。
     * 适用于请求级别的租户隔离场景。</p>
     * 
     * @param delegate 委托的状态存储实例
     * @return 租户隔离的状态存储
     */
    public static StateStoreCapability wrap(StateStoreCapability delegate) {
        return new TenantIsolatedStateStore(delegate);
    }

    // ==================== StateStoreCapability 实现 ====================

    /**
     * 获取存储的值
     * 
     * @param key  存储键（会自动添加租户前缀）
     * @param type 值的类型
     * @param <T>  值类型
     * @return 存储的值，不存在返回回 empty
     */
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(type, "type 不能为空");

        String tenantKey = tenantKey(key);
        log.debug("租户隔离读取: originalKey={}, tenantKey={}", key, tenantKey);

        return delegate.get(tenantKey, type);
    }

    /**
     * 存储值
     * 
     * @param key   存储键（会自动添加租户前缀）
     * @param value 要存储的值
     */
    @Override
    public void put(String key, Object value) {
        Objects.requireNonNull(key, "key 不能为空");

        String tenantKey = tenantKey(key);
        log.debug("租户隔离存储: originalKey={}, tenantKey={}", key, tenantKey);

        delegate.put(tenantKey, value);
    }

    /**
     * 存储值（带过期时间）
     * 
     * @param key   存储键（会自动添加租户前缀）
     * @param value 要存储的值
     * @param ttl   过期时间
     */
    @Override
    public void put(String key, Object value, Duration ttl) {
        Objects.requireNonNull(key, "key 不能为空");

        String tenantKey = tenantKey(key);
        log.debug("租户隔离存储: originalKey={}, tenantKey={}, ttl={}", key, tenantKey, ttl);

        delegate.put(tenantKey, value, ttl);
    }

    /**
     * 删除存储的值
     * 
     * @param key 存储键（会自动添加租户前缀）
     */
    @Override
    public void remove(String key) {
        Objects.requireNonNull(key, "key 不能为空");

        String tenantKey = tenantKey(key);
        log.debug("租户隔离删除: originalKey={}, tenantKey={}", key, tenantKey);

        delegate.remove(tenantKey);
    }

    /**
     * 检查键是否存在
     * 
     * @param key 存储键（会自动添加租户前缀）
     * @return 存在返回 true
     */
    @Override
    public boolean exists(String key) {
        Objects.requireNonNull(key, "key 不能为空");

        String tenantKey = tenantKey(key);
        return delegate.exists(tenantKey);
    }

    // ==================== 辅助方法 ====================

    /**
     * 生成租户隔离的 Key
     * 
     * <p>将原始 Key 与租户前缀拼接，生成唯一的隔离键。
     * 格式：tenant:{tenantId}:{originalKey}</p>
     * 
     * @param key 原始 Key
     * @return 带租户前缀的 Key
     */
    private String tenantKey(String key) {
        String tenantId = getCurrentTenantId();
        return String.format(TENANT_KEY_PREFIX, tenantId) + key;
    }

    /**
     * 获取当前租户ID
     * 
     * <p>优先使用固定租户ID，否则从 TenantContext 动态获取。
     * 动态模式下如果 TenantContext 未设置则抛出异常。</p>
     * 
     * @return 租户ID
     * @throws TenantContext.TenantNotSetException 如果使用动态租户但上下文未设置
     */
    private String getCurrentTenantId() {
        if (fixedTenantId != null) {
            return fixedTenantId;
        }

        // 从 TenantContext 动态获取
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new TenantContext.TenantNotSetException(
                    "动态租户隔离需要设置 TenantContext，请确保在请求处理链中设置了租户上下文");
        }
        return tenantId;
    }

    /**
     * 获取委托的状态存储
     * 
     * @return 委托实例
     */
    public StateStoreCapability getDelegate() {
        return delegate;
    }

    /**
     * 获取固定租户ID
     * 
     * @return 固定租户ID，动态模式返回 null
     */
    public String getFixedTenantId() {
        return fixedTenantId;
    }

    @Override
    public String toString() {
        return "TenantIsolatedStateStore{" +
                "tenantId=" + (fixedTenantId != null ? fixedTenantId : "dynamic") +
                ", delegate=" + delegate.getClass().getSimpleName() +
                '}';
    }
}
