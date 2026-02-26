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
 * 生命周期事件监听器
 * 
 * <p>监听模块生命周期变化事件。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface LifecycleListener {

    /**
     * 模块初始化前调用
     * 
     * @param moduleId 模块 ID
     */
    default void beforeInit(String moduleId) {}

    /**
     * 模块初始化后调用
     * 
     * @param moduleId 模块 ID
     * @param success 是否成功
     */
    default void afterInit(String moduleId, boolean success) {}

    /**
     * 模块启动前调用
     * 
     * @param moduleId 模块 ID
     */
    default void beforeStart(String moduleId) {}

    /**
     * 模块启动后调用
     * 
     * @param moduleId 模块 ID
     * @param success 是否成功
     */
    default void afterStart(String moduleId, boolean success) {}

    /**
     * 模块停止前调用
     * 
     * @param moduleId 模块 ID
     */
    default void beforeStop(String moduleId) {}

    /**
     * 模块停止后调用
     * 
     * @param moduleId 模块 ID
     */
    default void afterStop(String moduleId) {}

    /**
     * 模块销毁前调用
     * 
     * @param moduleId 模块 ID
     */
    default void beforeDestroy(String moduleId) {}

    /**
     * 模块销毁后调用
     * 
     * @param moduleId 模块 ID
     */
    default void afterDestroy(String moduleId) {}

    /**
     * 模块发生错误时调用
     * 
     * @param moduleId 模块 ID
     * @param phase 生命周期阶段
     * @param error 错误信息
     */
    default void onError(String moduleId, LifecyclePhase phase, Throwable error) {}
}
