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

/**
 * Static Asset Configuration.
 *
 * <p>Configures static assets contributed by a module.</p>
 * <p>【静态资源配置】配置模块贡献的静态资源。</p>
 *
 * <p>Extracted from ModuleManifest.java as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ResourceConfig
 */
public class AssetConfig {

    /**
     * Asset type.
     * 资源类型
     */
    private String type;

    /**
     * Asset path.
     * 资源路径
     */
    private String path;

    // ==================== Getters and Setters ====================

    public String getType() { 
        return type; 
    }
    
    public void setType(String type) { 
        this.type = type; 
    }
    
    public String getPath() { 
        return path; 
    }
    
    public void setPath(String path) { 
        this.path = path; 
    }

    @Override
    public String toString() {
        return "AssetConfig{" +
               "type='" + type + '\'' +
               ", path='" + path + '\'' +
               '}';
    }
}
