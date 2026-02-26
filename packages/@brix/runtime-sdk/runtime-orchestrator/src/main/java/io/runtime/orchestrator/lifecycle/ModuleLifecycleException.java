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
 * 模块生命周期异常
 * 
 * <p>当模块在生命周期操作中发生错误时抛出。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class ModuleLifecycleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 模块 ID
     */
    private final String moduleId;

    /**
     * 生命周期阶段
     */
    private final LifecyclePhase phase;

    /**
     * 创建模块生命周期异常
     * 
     * @param moduleId 模块 ID
     * @param phase 生命周期阶段
     * @param cause 原因异常
     */
    public ModuleLifecycleException(String moduleId, LifecyclePhase phase, Throwable cause) {
        super("Module lifecycle error [" + moduleId + "] at phase [" + phase + "]", cause);
        this.moduleId = moduleId;
        this.phase = phase;
    }

    /**
     * 创建模块生命周期异常
     * 
     * @param moduleId 模块 ID
     * @param phase 生命周期阶段
     * @param message 错误消息
     */
    public ModuleLifecycleException(String moduleId, LifecyclePhase phase, String message) {
        super("Module lifecycle error [" + moduleId + "] at phase [" + phase + "]: " + message);
        this.moduleId = moduleId;
        this.phase = phase;
    }

    /**
     * 获取模块 ID
     * 
     * @return 模块 ID
     */
    public String getModuleId() {
        return moduleId;
    }

    /**
     * 获取生命周期阶段
     * 
     * @return 生命周期阶段
     */
    public LifecyclePhase getPhase() {
        return phase;
    }
}
