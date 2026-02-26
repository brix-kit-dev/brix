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

import io.runtime.sdk.context.RuntimeContext;

/**
 * 生命周期能力契约
 * 
 * <p>定义模块的完整生命周期回调接口，由 Runtime Shell 在适当时机调用。
 * 模块实现此接口以响应生命周期事件，执行初始化、启动、停止、销毁等操作。</p>
 * 
 * <h3>生命周期状态机</h3>
 * <pre>{@code
 * REGISTERED -> INITIALIZING -> STARTING -> RUNNING -> STOPPING -> DESTROYED
 *                   |                          |
 *                   v                          v
 *               (失败)                     DEGRADED (降级运行)
 * }</pre>
 * 
 * <h3>回调顺序</h3>
 * <ol>
 *   <li>{@link #onInit(RuntimeContext)} - 依赖注入完成后调用</li>
 *   <li>{@link #onStart()} - 所有依赖模块启动后调用</li>
 *   <li>{@link #healthCheck()} - 运行期间定期调用</li>
 *   <li>{@link #onStop()} - 收到停止信号时调用</li>
 *   <li>{@link #onDestroy()} - 从运行时移除时调用</li>
 * </ol>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class BookingModule implements LifecycleCapability {
 *     private RuntimeContext context;
 *     private ScheduledExecutorService scheduler;
 *     
 *     @Override
 *     public void onInit(RuntimeContext context) {
 *         this.context = context;
 *         // 加载配置
 *         int poolSize = context.getConfigStore().getInt("booking.thread-pool-size", 10);
 *         this.scheduler = Executors.newScheduledThreadPool(poolSize);
 *     }
 *     
 *     @Override
 *     public void onStart() {
 *         // 启动后台任务
 *         scheduler.scheduleAtFixedRate(this::cleanExpiredBookings, 0, 1, TimeUnit.HOURS);
 *         
 *         // 发布模块就绪事件
 *         context.getEventBus().publishIntegration(new ModuleReadyEvent("booking"));
 *     }
 *     
 *     @Override
 *     public HealthStatus healthCheck() {
 *         // 检查数据库连接等
 *         return HealthStatus.UP;
 *     }
 *     
 *     @Override
 *     public void onStop() {
 *         // 优雅关闭
 *         scheduler.shutdown();
 *     }
 *     
 *     @Override
 *     public void onDestroy() {
 *         // 清理资源
 *         scheduler.shutdownNow();
 *     }
 * }
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see RuntimeContext
 * @see HealthStatus
 * @see ModuleMetadata
 */
public interface LifecycleCapability {

    /**
     * 模块初始化回调
     * 
     * <p>在依赖注入完成后、模块启动前调用。用于：</p>
     * <ul>
     *   <li>加载配置</li>
     *   <li>初始化资源池（线程池、连接池）</li>
     *   <li>建立外部连接</li>
     *   <li>注册内部组件</li>
     * </ul>
     * 
     * <p><b>注意</b>：此时其他模块可能尚未初始化完成，不要调用其他模块的服务</p>
     * 
     * @param context 运行时上下文，提供所有能力的访问入口
     * @throws ModuleInitializationException 如果初始化失败
     */
    void onInit(RuntimeContext context);

    /**
     * 模块启动回调
     * 
     * <p>在所有依赖模块启动完成后调用。用于：</p>
     * <ul>
     *   <li>注册 API 路由</li>
     *   <li>启动后台任务</li>
     *   <li>订阅事件</li>
     *   <li>发布"模块已就绪"事件</li>
     * </ul>
     * 
     * <p>此时可以安全地与其他模块交互</p>
     * 
     * @throws ModuleStartupException 如果启动失败
     */
    void onStart();

    /**
     * 模块停止回调
     * 
     * <p>收到停止信号时调用，用于优雅关闭。应该：</p>
     * <ul>
     *   <li>停止接收新请求</li>
     *   <li>等待进行中的请求完成</li>
     *   <li>保存必要的状态</li>
     *   <li>释放资源</li>
     * </ul>
     * 
     * <p><b>超时处理</b>：应在 manifest 配置的 graceful-shutdown.timeout 内完成</p>
     */
    void onStop();

    /**
     * 模块销毁回调
     * 
     * <p>模块从运行时完全移除时调用。用于：</p>
     * <ul>
     *   <li>清理临时文件</li>
     *   <li>注销外部注册（服务发现等）</li>
     *   <li>关闭所有连接</li>
     * </ul>
     * 
     * <p>此方法调用后，模块实例将被垃圾回收</p>
     */
    void onDestroy();

    /**
     * 健康检查
     * 
     * <p>Runtime Shell 定期调用此方法检查模块健康状态。
     * 检查项建议包括：</p>
     * <ul>
     *   <li>数据库连接是否正常</li>
     *   <li>外部服务是否可达</li>
     *   <li>关键资源是否可用</li>
     * </ul>
     * 
     * @return 健康状态
     * @see HealthStatus
     */
    HealthStatus healthCheck();

    /**
     * 获取模块元数据
     * 
     * <p>返回模块的基本信息，用于注册和监控</p>
     * 
     * @return 模块元数据
     * @see ModuleMetadata
     */
    ModuleMetadata getMetadata();
}
