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
package io.runtime.sdk.capability;

import java.time.Duration;
import java.util.Optional;

/**
 * 状态存储能力契约
 * 
 * <p>提供键值存储的抽象接口，用于缓存、会话、临时数据存储等场景。
 * 模块通过此接口操作状态数据，无需感知底层实现（Redis/Memcached/本地内存）。</p>
 * 
 * <h3>核心职责</h3>
 * <ul>
 *   <li>键值存储：支持任意类型的值存取</li>
 *   <li>过期策略：支持 TTL 自动过期</li>
 *   <li>存在性检查：高效判断键是否存在</li>
 * </ul>
 * 
 * <h3>使用场景</h3>
 * <ul>
 *   <li>缓存热点数据</li>
 *   <li>存储用户会话信息</li>
 *   <li>临时状态数据（如验证码）</li>
 *   <li>跨请求的上下文传递</li>
 * </ul>
 * 
 * <h3>键命名规范</h3>
 * <p>建议使用冒号分隔的命名空间：{模块}:{类型}:{标识}</p>
 * <pre>{@code
 * // 示例
 * "booking:session:user123"
 * "identity:captcha:phone-13800138000"
 * "contract:cache:contract-456"
 * }</pre>
 * 
 * <h3>序列化说明</h3>
 * <p>值对象会被序列化为 JSON 存储，需确保：</p>
 * <ul>
 *   <li>值对象有无参构造函数（或 Jackson 兼容的构造方式）</li>
 *   <li>字段有 getter/setter 或使用 Jackson 注解</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Inject
 * private StateStoreCapability stateStore;
 * 
 * public void cacheUser(User user) {
 *     // 缓存用户信息，30分钟过期
 *     stateStore.put("user:cache:" + user.getId(), user, Duration.ofMinutes(30));
 * }
 * 
 * public Optional<User> getUser(String userId) {
 *     return stateStore.get("user:cache:" + userId, User.class);
 * }
 * }</pre>
 * 
 * <h3>实现说明</h3>
 * <p>此接口由 Host 层实现：</p>
 * <ul>
 *   <li>Full Product Host：Redis 实现</li>
 *   <li>Embedded Host：本地 ConcurrentHashMap 或客户系统提供的缓存</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface StateStoreCapability {

    /**
     * 获取存储的值
     * 
     * @param key  存储键，不能为空
     * @param type 值的类型，用于反序列化
     * @param <T>  值类型
     * @return 存储的值，如果不存在返回回 {@link Optional#empty()}
     * @throws IllegalArgumentException 如果 key 或 type 为 null
     */
    <T> Optional<T> get(String key, Class<T> type);

    /**
     * 存储值（无过期时间）
     * 
     * <p>注意：无过期时间的数据会一直存在，请谨慎使用，避免内存泄漏</p>
     * 
     * @param key   存储键，不能为空
     * @param value 要存储的值，不能为 null
     * @throws IllegalArgumentException 如果 key 或 value 为 null
     */
    void put(String key, Object value);

    /**
     * 存储值（带过期时间）
     * 
     * <p>推荐使用此方法，明确指定过期时间</p>
     * 
     * @param key   存储键，不能为空
     * @param value 要存储的值，不能为 null
     * @param ttl   过期时间，不能为 null 或负数
     * @throws IllegalArgumentException 如果参数无效
     */
    void put(String key, Object value, Duration ttl);

    /**
     * 删除存储的值
     * 
     * <p>如果键不存在，此方法不会抛出异常</p>
     * 
     * @param key 存储键，不能为空
     * @throws IllegalArgumentException 如果 key 为 null
     */
    void remove(String key);

    /**
     * 检查键是否存在
     * 
     * <p>此方法比 get() 更高效，仅检查存在性而不反序列化值</p>
     * 
     * @param key 存储键，不能为空
     * @return 如果键存在返回 true，否则返回 false
     * @throws IllegalArgumentException 如果 key 为 null
     */
    boolean exists(String key);

    /**
     * 获取并删除值（原子操作）
     * 
     * <p>常用于一次性验证码、令牌等场景</p>
     * 
     * @param key  存储键，不能为空
     * @param type 值的类型
     * @param <T>  值类型
     * @return 存储的值，如果不存在返回回 {@link Optional#empty()}
     */
    default <T> Optional<T> getAndRemove(String key, Class<T> type) {
        Optional<T> value = get(key, type);
        value.ifPresent(v -> remove(key));
        return value;
    }

    /**
     * 如果不存在则存储（原子操作）
     * 
     * <p>用于实现简单的分布式锁或防重复提交</p>
     * 
     * @param key   存储键
     * @param value 要存储的值
     * @param ttl   过期时间
     * @return 如果成功存储返回 true，如果键已存在返回 false
     */
    default boolean putIfAbsent(String key, Object value, Duration ttl) {
        if (!exists(key)) {
            put(key, value, ttl);
            return true;
        }
        return false;
    }
}
