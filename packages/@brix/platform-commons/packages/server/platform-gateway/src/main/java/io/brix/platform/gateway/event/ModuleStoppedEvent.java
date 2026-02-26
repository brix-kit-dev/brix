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
 * 模块停止事件
 * 
 * <p>当模块停止时，Runtime Orchestrator 发布此事件。
 * 网关监听此事件以移除模块注册的路由。</p>
 * 
 * <h3>事件来源</h3>
 * <p>由 Runtime Orchestrator 在模块 STOPPED 状态时发布，
 * 通过 EventBusCapability 传递到网关。</p>
 * 
 * <h3>事件数据</h3>
 * <ul>
 *   <li>moduleId - 模块唯一标识</li>
 *   <li>reason - 停止原因</li>
 *   <li>graceful - 是否优雅停止</li>
 *   <li>timestamp - 事件发生时间</li>
 * </ul>
 * 
 * <h3>与 Manifest 的关系</h3>
 * <p>对应 module-manifest.yaml 中 events.subscriptions 配置：</p>
 * <pre>{@code
 * events:
 *   subscriptions:
 *     - topic: "brix.module.lifecycle"
 *       event-type: "ModuleStoppedEvent"
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
public class ModuleStoppedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 停止原因枚举
     */
    public enum StopReason {
        /** 正常停止 */
        NORMAL,
        /** 被卸载 */
        UNLOAD,
        /** 错误导致 */
        ERROR,
        /** 超时 */
        TIMEOUT,
        /** 系统关闭 */
        SHUTDOWN
    }

    /**
     * 模块唯一标识
     */
    private final String moduleId;

    /**
     * 停止原因
     */
    private final StopReason reason;

    /**
     * 停止原因描述
     */
    private final String reasonMessage;

    /**
     * 是否优雅停止
     */
    private final boolean graceful;

    /**
     * 事件发生时间
     */
    private final Instant timestamp;

    /**
     * 构造函数
     * 
     * @param source        事件源
     * @param moduleId      模块唯一标识
     * @param reason        停止原因
     * @param reasonMessage 停止原因描述
     * @param graceful      是否优雅停止
     */
    public ModuleStoppedEvent(Object source,
                              String moduleId,
                              StopReason reason,
                              String reasonMessage,
                              boolean graceful) {
        super(source);
        this.moduleId = moduleId;
        this.reason = reason != null ? reason : StopReason.NORMAL;
        this.reasonMessage = reasonMessage;
        this.graceful = graceful;
        this.timestamp = Instant.now();
    }

    /**
     * 简化构造函数（正常停止）
     * 
     * @param source   事件源
     * @param moduleId 模块唯一标识
     */
    public ModuleStoppedEvent(Object source, String moduleId) {
        this(source, moduleId, StopReason.NORMAL, null, true);
    }

    /**
     * 获取模块唯一标识
     * 
     * @return 模块 ID
     */
    public String getModuleId() {
        return moduleId;
    }

    /**
     * 获取停止原因
     * 
     * @return 停止原因枚举
     */
    public StopReason getReason() {
        return reason;
    }

    /**
     * 获取停止原因描述
     * 
     * @return 原因描述
     */
    public String getReasonMessage() {
        return reasonMessage;
    }

    /**
     * 是否优雅停止
     * 
     * @return true 表示优雅停止
     */
    public boolean isGraceful() {
        return graceful;
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
        return "ModuleStoppedEvent{" +
                "moduleId='" + moduleId + '\'' +
                ", reason=" + reason +
                ", reasonMessage='" + reasonMessage + '\'' +
                ", graceful=" + graceful +
                ", timestamp=" + timestamp +
                '}';
    }
}
