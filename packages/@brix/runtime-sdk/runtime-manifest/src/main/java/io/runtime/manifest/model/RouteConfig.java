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
 * Route Configuration.
 *
 * <p>Configures route path, allowed HTTP methods, and security requirements.</p>
 * <p>【路由配置】配置路由路径、允许的HTTP方法和安全要求。</p>
 *
 * <p>Extracted from ModuleManifest.java as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ResourceConfig
 */
public class RouteConfig {

    /**
     * Route path.
     * 路由路径
     */
    private String path;

    /**
     * Allowed HTTP methods.
     * 允许的 HTTP 方法
     */
    private List<String> methods = new ArrayList<>();

    /**
     * Whether authentication is required.
     * 是否需要认证
     */
    private boolean authenticated = true;

    /**
     * Required permissions.
     * 所需权限
     */
    private List<String> permissions = new ArrayList<>();

    // ==================== Getters and Setters ====================

    public String getPath() { 
        return path; 
    }
    
    public void setPath(String path) { 
        this.path = path; 
    }
    
    public List<String> getMethods() { 
        return methods; 
    }
    
    public void setMethods(List<String> methods) { 
        this.methods = methods != null ? methods : new ArrayList<>(); 
    }
    
    public boolean isAuthenticated() { 
        return authenticated; 
    }
    
    public void setAuthenticated(boolean authenticated) { 
        this.authenticated = authenticated; 
    }
    
    public List<String> getPermissions() { 
        return permissions; 
    }
    
    public void setPermissions(List<String> permissions) { 
        this.permissions = permissions != null ? permissions : new ArrayList<>(); 
    }

    @Override
    public String toString() {
        return "RouteConfig{" +
               "path='" + path + '\'' +
               ", methods=" + methods +
               ", authenticated=" + authenticated +
               '}';
    }
}
