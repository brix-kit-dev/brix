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

/**
 * 生命周期管理器状态
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public enum LifecycleManagerState {

    /**
     * 已创建，尚未初始化
     */
    CREATED("已创建"),

    /**
     * 正在初始化模块
     */
    INITIALIZING("初始化中"),

    /**
     * 所有模块已初始化
     */
    INITIALIZED("已初始化"),

    /**
     * 正在启动模块
     */
    STARTING("启动中"),

    /**
     * 所有模块已启动运行
     */
    RUNNING("运行中"),

    /**
     * 正在停止模块
     */
    STOPPING("停止中"),

    /**
     * 所有模块已停止
     */
    STOPPED("已停止"),

    /**
     * 发生错误
     */
    ERROR("错误");

    private final String description;

    LifecycleManagerState(String description) {
        this.description = description;
    }

    /**
     * 获取状态描述
     * 
     * @return 状态描述
     */
    public String getDescription() {
        return description;
    }
}
