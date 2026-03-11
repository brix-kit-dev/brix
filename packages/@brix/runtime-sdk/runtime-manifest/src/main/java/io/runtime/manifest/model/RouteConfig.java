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
     */
    private String path;

    /**
     * Allowed HTTP methods.
     */
    private List<String> methods = new ArrayList<>();

    /**
     * Whether authentication is required.
     */
    private boolean authenticated = true;

    /**
     * Required permissions.
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
