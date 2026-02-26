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
package io.runtime.orchestrator.registry;

/**
 * 模块已注册异常
 * 
 * <p>当尝试注册一个已存在的模块 ID 时抛出。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class ModuleAlreadyRegisteredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 模块 ID
     */
    private final String moduleId;

    /**
     * 创建模块已注册异常
     * 
     * @param moduleId 模块 ID
     */
    public ModuleAlreadyRegisteredException(String moduleId) {
        super("Module already registered: " + moduleId);
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
