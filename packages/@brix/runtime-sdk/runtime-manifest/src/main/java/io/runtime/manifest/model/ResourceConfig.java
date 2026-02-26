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
 * Resource Configuration.
 *
 * <p>Defines routes, menus, and static assets contributed by a module.</p>
 * <p>【资源配置】定义模块贡献的路由、菜单和静态资源。</p>
 *
 * <p>Extracted from ModuleManifest.java as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ModuleManifest
 */
public class ResourceConfig {

    /**
     * Route configuration.
     * 路由配置
     */
    private List<RouteConfig> routes = new ArrayList<>();

    /**
     * Menu configuration.
     * 菜单配置
     */
    private List<MenuConfig> menus = new ArrayList<>();

    /**
     * Static asset configuration.
     * 静态资源配置
     */
    private List<AssetConfig> assets = new ArrayList<>();

    // ==================== Getters and Setters ====================

    public List<RouteConfig> getRoutes() { 
        return routes; 
    }
    
    public void setRoutes(List<RouteConfig> routes) { 
        this.routes = routes != null ? routes : new ArrayList<>(); 
    }
    
    public List<MenuConfig> getMenus() { 
        return menus; 
    }
    
    public void setMenus(List<MenuConfig> menus) { 
        this.menus = menus != null ? menus : new ArrayList<>(); 
    }
    
    public List<AssetConfig> getAssets() { 
        return assets; 
    }
    
    public void setAssets(List<AssetConfig> assets) { 
        this.assets = assets != null ? assets : new ArrayList<>(); 
    }

    @Override
    public String toString() {
        return "ResourceConfig{" +
               "routes=" + routes.size() +
               ", menus=" + menus.size() +
               ", assets=" + assets.size() +
               '}';
    }
}
