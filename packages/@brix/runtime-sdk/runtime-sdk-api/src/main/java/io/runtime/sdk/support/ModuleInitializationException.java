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
 * 模块初始化异常
 * 
 * <p>当模块在初始化阶段（onInit）发生错误时抛出。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class ModuleInitializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 模块 ID
     */
    private final String moduleId;

    /**
     * 创建模块初始化异常
     * 
     * @param moduleId 模块 ID
     * @param cause    原因异常
     */
    public ModuleInitializationException(String moduleId, Throwable cause) {
        super("Failed to initialize module: " + moduleId, cause);
        this.moduleId = moduleId;
    }

    /**
     * 创建模块初始化异常
     * 
     * @param moduleId 模块 ID
     * @param message  异常消息
     */
    public ModuleInitializationException(String moduleId, String message) {
        super("Failed to initialize module [" + moduleId + "]: " + message);
        this.moduleId = moduleId;
    }

    /**
     * 获取模块 ID
     * 
     * @return 模块 ID
     */
    public String getModuleId() {
        return moduleId;
    }
}
