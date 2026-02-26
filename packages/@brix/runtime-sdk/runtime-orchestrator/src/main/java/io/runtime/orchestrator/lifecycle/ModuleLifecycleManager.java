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
package io.runtime.orchestrator.lifecycle;

import io.runtime.manifest.model.ModuleManifest;
import io.runtime.sdk.capability.HealthStatus;
import io.runtime.sdk.context.RuntimeContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 模块生命周期管理器
 * 
 * <p>负责管理所有模块的生命周期，包括初始化、启动、健康检查和停止。
 * 支持有序启动（按依赖和 startupOrder）和优雅停止。</p>
 * 
 * <h3>核心职责</h3>
 * <ul>
 *   <li>按依赖顺序初始化和启动模块</li>
 *   <li>执行定期健康检查</li>
 *   <li>按逆序停止模块（优雅关闭）</li>
 *   <li>处理模块生命周期事件</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 创建管理器
 * ModuleLifecycleManager manager = new DefaultModuleLifecycleManager(registry, contextFactory);
 * 
 * // 初始化所有模块
 * manager.initializeAll().join();
 * 
 * // 启动所有模块
 * manager.startAll().join();
 * 
 * // 获取健康状态
 * Map<String, HealthStatus> health = manager.checkHealth();
 * 
 * // 优雅停止
 * manager.stopAll().join();
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface ModuleLifecycleManager {

    /**
     * 初始化所有已注册模块
     * 
     * <p>按 startupOrder 和依赖顺序初始化模块</p>
     * 
     * @return CompletableFuture 表示初始化完成
     */
    CompletableFuture<Void> initializeAll();

    /**
     * 初始化指定模块
     * 
     * @param moduleId 模块 ID
     * @return CompletableFuture 表示初始化完成
     */
    CompletableFuture<Void> initialize(String moduleId);

    /**
     * 启动所有已初始化模块
     * 
     * <p>按 startupOrder 和依赖顺序启动模块</p>
     * 
     * @return CompletableFuture 表示启动完成
     */
    CompletableFuture<Void> startAll();

    /**
     * 启动指定模块
     * 
     * @param moduleId 模块 ID
     * @return CompletableFuture 表示启动完成
     */
    CompletableFuture<Void> start(String moduleId);

    /**
     * 停止所有运行中模块
     * 
     * <p>按 startupOrder 逆序停止模块，确保依赖先停止</p>
     * 
     * @return CompletableFuture 表示停止完成
     */
    CompletableFuture<Void> stopAll();

    /**
     * 停止指定模块
     * 
     * @param moduleId 模块 ID
     * @return CompletableFuture 表示停止完成
     */
    CompletableFuture<Void> stop(String moduleId);

    /**
     * 销毁所有模块
     * 
     * <p>释放所有模块资源</p>
     * 
     * @return CompletableFuture 表示销毁完成
     */
    CompletableFuture<Void> destroyAll();

    /**
     * 检查所有模块健康状态
     * 
     * @return 模块ID -> 健康状态 的映射
     */
    Map<String, HealthStatus> checkHealth();

    /**
     * 检查指定模块健康状态
     * 
     * @param moduleId 模块 ID
     * @return 健康状态
     */
    HealthStatus checkHealth(String moduleId);

    /**
     * 重启指定模块
     * 
     * @param moduleId 模块 ID
     * @return CompletableFuture 表示重启完成
     */
    CompletableFuture<Void> restart(String moduleId);

    /**
     * 设置运行时上下文工厂
     * 
     * @param contextFactory 上下文工厂
     */
    void setContextFactory(RuntimeContextFactory contextFactory);

    /**
     * 添加生命周期监听器
     * 
     * @param listener 生命周期监听器
     */
    void addListener(LifecycleListener listener);

    /**
     * 移除生命周期监听器
     * 
     * @param listener 生命周期监听器
     */
    void removeListener(LifecycleListener listener);

    /**
     * 获取管理器状态
     * 
     * @return 管理器当前状态
     */
    LifecycleManagerState getState();

    /**
     * 设置能力提供者
     * 
     * <p>能力提供者用于验证模块所需的能力是否可用</p>
     * 
     * @param capabilityProvider 能力提供者
     */
    void setCapabilityProvider(CapabilityProvider capabilityProvider);

    /**
     * 验证模块的能力依赖
     * 
     * <p>检查模块 manifest 中声明的必需能力是否都被 Host 提供。
     * 如果存在缺失的必需能力，将抛出 {@link CapabilityMissingException}。</p>
     * 
     * @param manifest 模块清单
     * @throws CapabilityMissingException 如果必需能力缺失
     */
    void validateCapabilities(ModuleManifest manifest);

    /**
     * 运行时上下文工厂
     * 
     * <p>用于为每个模块创建运行时上下文</p>
     */
    @FunctionalInterface
    interface RuntimeContextFactory {
        /**
         * 为指定模块创建运行时上下文
         * 
         * @param moduleId 模块 ID
         * @return 运行时上下文
         */
        RuntimeContext createContext(String moduleId);
    }

    /**
     * 能力提供者接口
     * 
     * <p>用于查询 Host 提供的能力</p>
     */
    interface CapabilityProvider {
        
        /**
         * 检查是否提供指定能力
         * 
         * @param capability 能力标识（如 "event-bus", "state-store"）
         * @return 如果提供该能力返回 true
         */
        boolean hasCapability(String capability);
        
        /**
         * 获取所有提供的能力
         * 
         * @return 能力标识集合
         */
        Set<String> getCapabilities();
    }
}
