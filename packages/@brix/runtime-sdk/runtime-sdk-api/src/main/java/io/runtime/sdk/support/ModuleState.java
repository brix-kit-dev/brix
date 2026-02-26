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
package io.runtime.sdk.support;

/**
 * 模块状态枚举
 * 
 * <p>定义模块在生命周期中的各种状态。</p>
 * 
 * <h3>状态转换</h3>
 * <pre>{@code
 * REGISTERED -> INITIALIZING -> INITIALIZED -> STARTING -> RUNNING -> STOPPING -> STOPPED -> DESTROYED
 *                    |                             |                       |
 *                    v                             v                       |
 *                 FAILED                       DEGRADED <-----------------+
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public enum ModuleState {

    /**
     * 已注册状态
     * 
     * <p>模块类已被发现并注册，但尚未初始化</p>
     */
    REGISTERED("已注册"),

    /**
     * 初始化中状态
     * 
     * <p>模块正在执行 onInit() 方法</p>
     */
    INITIALIZING("初始化中"),

    /**
     * 已初始化状态
     * 
     * <p>模块 onInit() 执行完成，等待启动</p>
     */
    INITIALIZED("已初始化"),

    /**
     * 启动中状态
     * 
     * <p>模块正在执行 onStart() 方法</p>
     */
    STARTING("启动中"),

    /**
     * 运行中状态
     * 
     * <p>模块正常运行，可以处理请求</p>
     */
    RUNNING("运行中"),

    /**
     * 降级状态
     * 
     * <p>模块部分功能不可用，但核心功能正常</p>
     */
    DEGRADED("降级运行"),

    /**
     * 停止中状态
     * 
     * <p>模块正在执行 onStop() 方法</p>
     */
    STOPPING("停止中"),

    /**
     * 已停止状态
     * 
     * <p>模块已停止，不再处理请求</p>
     */
    STOPPED("已停止"),

    /**
     * 已销毁状态
     * 
     * <p>模块已销毁，资源已释放</p>
     */
    DESTROYED("已销毁"),

    /**
     * 失败状态
     * 
     * <p>模块初始化或启动失败</p>
     */
    FAILED("失败");

    /**
     * 状态描述
     */
    private final String description;

    ModuleState(String description) {
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

    /**
     * 判断模块是否可用（可以处理请求）
     * 
     * @return 如果模块可用返回 true
     */
    public boolean isAvailable() {
        return this == RUNNING || this == DEGRADED;
    }

    /**
     * 判断模块是否处于终止状态
     * 
     * @return 如果模块已终止返回 true
     */
    public boolean isTerminal() {
        return this == STOPPED || this == DESTROYED || this == FAILED;
    }
}
