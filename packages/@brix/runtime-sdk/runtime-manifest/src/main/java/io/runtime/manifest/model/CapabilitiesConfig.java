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
package io.runtime.manifest.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Capabilities Configuration.
 *
 * <p>Declares runtime capabilities required by the module, divided into required and optional.</p>
 * <p>【能力配置】声明模块运行所需的能力依赖，分为必需能力和可选能力。</p>
 *
 * <h4>Example Configuration</h4>
 * <pre>{@code
 * capabilities:
 *   required:
 *     - event-bus
 *     - state-store
 *     - auth-context
 *   optional:
 *     - scheduling
 *     - lock
 *     - resilience
 * }</pre>
 *
 * <p>Extracted from ModuleManifest.java as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ModuleManifest
 */
public class CapabilitiesConfig {

    /**
     * Required capabilities list (startup fails if missing).
     * 必需能力列表（缺失则启动失败）
     */
    private List<String> required = new ArrayList<>();

    /**
     * Optional capabilities list (features degrade if missing).
     * 可选能力列表（缺失则功能降级）
     */
    private List<String> optional = new ArrayList<>();

    // ==================== Getters and Setters ====================

    public List<String> getRequired() { 
        return required; 
    }
    
    public void setRequired(List<String> required) { 
        this.required = required != null ? required : new ArrayList<>(); 
    }
    
    public List<String> getOptional() { 
        return optional; 
    }
    
    public void setOptional(List<String> optional) { 
        this.optional = optional != null ? optional : new ArrayList<>(); 
    }

    @Override
    public String toString() {
        return "CapabilitiesConfig{" +
               "required=" + required.size() + 
               ", optional=" + optional.size() + 
               '}';
    }
}
