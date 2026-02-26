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
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁能力契约（可选能力）
 * 
 * <p>提供分布式锁的统一抽象，用于跨进程的并发控制。
 * 模块通过此接口获取和释放锁，无需感知底层实现（Redis/ZooKeeper/数据库）。</p>
 * 
 * <h3>核心功能</h3>
 * <ul>
 *   <li><b>排他锁</b>：同一时间只有一个持有者</li>
 *   <li><b>超时机制</b>：防止死锁</li>
 *   <li><b>可重入性</b>：同一线程可重复获取</li>
 * </ul>
 * 
 * <h3>使用场景</h3>
 * <ul>
 *   <li>分布式定时任务（确保只有一个节点执行）</li>
 *   <li>库存扣减（防止超卖）</li>
 *   <li>订单处理（防止重复处理）</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Inject
 * private LockCapability lockCapability;
 * 
 * // 方式一：try-with-resources（推荐）
 * public void processOrder(String orderId) {
 *     String lockKey = "order:process:" + orderId;
 *     try (DistributedLock lock = lockCapability.acquire(lockKey, Duration.ofSeconds(30))) {
 *         if (lock.isLocked()) {
 *             // 执行订单处理逻辑...
 *         } else {
 *             throw new ConcurrentModificationException("Order is being processed");
 *         }
 *     }
 * }
 * 
 * // 方式二：手动获取和释放
 * public void processOrderManual(String orderId) {
 *     String lockKey = "order:process:" + orderId;
 *     if (lockCapability.tryLock(lockKey, 5, TimeUnit.SECONDS)) {
 *         try {
 *             // 执行订单处理逻辑...
 *         } finally {
 *             lockCapability.unlock(lockKey);
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <h3>注意事项</h3>
 * <ul>
 *   <li>锁的 key 应有业务含义，避免过于宽泛</li>
 *   <li>设置合理的超时时间，防止死锁</li>
 *   <li>确保在 finally 中释放锁</li>
 *   <li>此为可选能力，使用前应检查是否可用</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface LockCapability {

    /**
     * 获取分布式锁
     * 
     * <p>阻塞等待直到获取锁或超时</p>
     * 
     * @param key     锁的唯一键
     * @param timeout 最大等待时间
     * @return 锁对象，可用于 try-with-resources
     */
    DistributedLock acquire(String key, Duration timeout);

    /**
     * 尝试获取锁（非阻塞）
     * 
     * <p>立即返回，不等待</p>
     * 
     * @param key 锁的唯一键
     * @return 如果获取成功返回 true
     */
    boolean tryLock(String key);

    /**
     * 尝试获取锁（带等待时间）
     * 
     * @param key      锁的唯一键
     * @param waitTime 等待时间
     * @param unit     时间单位
     * @return 如果获取成功返回 true
     */
    boolean tryLock(String key, long waitTime, TimeUnit unit);

    /**
     * 释放锁
     * 
     * @param key 锁的唯一键
     */
    void unlock(String key);

    /**
     * 检查锁是否被持有
     * 
     * @param key 锁的唯一键
     * @return 如果锁被持有返回 true
     */
    boolean isLocked(String key);

    /**
     * 检查当前线程是否持有锁
     * 
     * @param key 锁的唯一键
     * @return 如果当前线程持有锁返回 true
     */
    boolean isHeldByCurrentThread(String key);
}
