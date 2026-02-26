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

/**
 * 分布式锁接口
 * 
 * <p>表示一个已获取或尝试获取的分布式锁，支持 try-with-resources 自动释放。</p>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * try (DistributedLock lock = lockCapability.acquire("my-lock", Duration.ofSeconds(10))) {
 *     if (lock.isLocked()) {
 *         // 执行需要锁保护的操作
 *     }
 * } // 自动释放锁
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see LockCapability#acquire(String, java.time.Duration)
 */
public interface DistributedLock extends AutoCloseable {

    /**
     * 获取锁的键
     * 
     * @return 锁的唯一键
     */
    String getKey();

    /**
     * 检查是否成功获取了锁
     * 
     * @return 如果持有锁返回 true
     */
    boolean isLocked();

    /**
     * 手动释放锁
     * 
     * <p>如果使用 try-with-resources，无需手动调用此方法</p>
     */
    void release();

    /**
     * 实现 AutoCloseable，自动释放锁
     */
    @Override
    default void close() {
        if (isLocked()) {
            release();
        }
    }
}
