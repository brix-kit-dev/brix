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
package io.brix.platform.gateway.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * 路由刷新请求事件
 * 
 * <p>运维人员或系统触发的路由强制刷新事件。
 * 网关监听此事件以重新加载所有路由配置。</p>
 * 
 * <h3>使用场景</h3>
 * <ul>
 *   <li>路由配置变更后手动刷新</li>
 *   <li>排查路由问题时重新加载</li>
 *   <li>灾难恢复场景</li>
 *   <li>运维监控检测到路由异常时自动触发</li>
 * </ul>
 * 
 * <h3>触发方式</h3>
 * <p>可通过以下方式触发：</p>
 * <ul>
 *   <li>运维 API 接口</li>
 *   <li>EventBus 发布事件</li>
 *   <li>定时任务检查</li>
 * </ul>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
public class RouteRefreshRequestedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 刷新原因
     */
    private final String reason;

    /**
     * 请求来源（如：api, scheduler, operator）
     */
    private final String source;

    /**
     * 是否强制刷新（忽略缓存）
     */
    private final boolean force;

    /**
     * 指定刷新的模块 ID（为空则刷新全部）
     */
    private final String targetModuleId;

    /**
     * 事件发生时间
     */
    private final Instant timestamp;

    /**
     * 构造函数
     * 
     * @param eventSource    事件发布者
     * @param reason         刷新原因
     * @param requestSource  请求来源
     * @param force          是否强制刷新
     * @param targetModuleId 目标模块 ID
     */
    public RouteRefreshRequestedEvent(Object eventSource,
                                      String reason,
                                      String requestSource,
                                      boolean force,
                                      String targetModuleId) {
        super(eventSource);
        this.reason = reason != null ? reason : "Manual refresh";
        this.source = requestSource != null ? requestSource : "unknown";
        this.force = force;
        this.targetModuleId = targetModuleId;
        this.timestamp = Instant.now();
    }

    /**
     * 简化构造函数
     * 
     * @param eventSource 事件发布者
     * @param reason      刷新原因
     */
    public RouteRefreshRequestedEvent(Object eventSource, String reason) {
        this(eventSource, reason, null, false, null);
    }

    /**
     * 获取刷新原因
     * 
     * @return 原因描述
     */
    public String getReason() {
        return reason;
    }

    /**
     * 获取请求来源
     * 
     * @return 来源标识
     */
    public String getRequestSource() {
        return source;
    }

    /**
     * 是否强制刷新
     * 
     * @return true 表示忽略缓存强制刷新
     */
    public boolean isForce() {
        return force;
    }

    /**
     * 获取目标模块 ID
     * 
     * @return 模块 ID，为空则刷新全部
     */
    public String getTargetModuleId() {
        return targetModuleId;
    }

    /**
     * 是否刷新全部路由
     * 
     * @return true 表示刷新全部
     */
    public boolean isRefreshAll() {
        return targetModuleId == null || targetModuleId.isEmpty();
    }

    /**
     * 获取事件发生时间
     * 
     * @return 时间戳
     */
    public Instant getEventTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "RouteRefreshRequestedEvent{" +
                "reason='" + reason + '\'' +
                ", source='" + source + '\'' +
                ", force=" + force +
                ", targetModuleId='" + targetModuleId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
